package com.github.prologdb.runtime.builtin

import com.github.prologdb.async.LazySequenceBuilder
import com.github.prologdb.async.Principal
import com.github.prologdb.runtime.Clause
import com.github.prologdb.runtime.ClauseIndicator
import com.github.prologdb.runtime.FullyQualifiedClauseIndicator
import com.github.prologdb.runtime.PrologException
import com.github.prologdb.runtime.PrologRuntimeEnvironment
import com.github.prologdb.runtime.PrologRuntimeException
import com.github.prologdb.runtime.PrologStackTraceElement
import com.github.prologdb.runtime.RandomVariableScope
import com.github.prologdb.runtime.analyzation.constraint.DeterminismLevel
import com.github.prologdb.runtime.analyzation.constraint.InvocationBehaviour
import com.github.prologdb.runtime.module.FullModuleImport
import com.github.prologdb.runtime.module.Module
import com.github.prologdb.runtime.module.ModuleImport
import com.github.prologdb.runtime.module.ModuleReference
import com.github.prologdb.runtime.module.ModuleScopeProofSearchContext
import com.github.prologdb.runtime.proofsearch.ASTPrologPredicate
import com.github.prologdb.runtime.proofsearch.Authorization
import com.github.prologdb.runtime.proofsearch.BehaviourExposingPrologCallable
import com.github.prologdb.runtime.proofsearch.PrologCallable
import com.github.prologdb.runtime.proofsearch.ProofSearchContext
import com.github.prologdb.runtime.proofsearch.Rule
import com.github.prologdb.runtime.query.PredicateInvocationQuery
import com.github.prologdb.runtime.query.Query
import com.github.prologdb.runtime.term.CompoundTerm
import com.github.prologdb.runtime.term.Term
import com.github.prologdb.runtime.term.Variable
import com.github.prologdb.runtime.unification.Unification
import java.util.Collections
import java.util.WeakHashMap

internal val A = Variable("A")
internal val B = Variable("B")
internal val C = Variable("C")
internal val X = Variable("X")


/**
 * Provides the implementation to a builtin. Is intended to be used **ONLY** in
 * combination with [nativeRule] to help ensure all preconditions for [invoke].
 *
 * Is invoked when the builtin is invoked from prolog. When invoked, it must be assured that:
 * * the predicate invoked from the prolog code actually matches the builtin (functor & arity)
 *
 * Arguments to the function:
 * 1. The arguments given to the builtin from the prolog code that invokes it
 * 2. The knowledge base within which the builtin is being executed
 * 3. Source of random variables to prevent collisions
 */
typealias PrologBuiltinImplementation = suspend LazySequenceBuilder<Unification>.(Array<out Term>, ProofSearchContext) -> Unit

/**
 * [Variable]s to be used in the "prolog"-ish representation of builtins. E.g.
 * when defining the builtin `string_chars(A, B)`, `A` and `B` are obtained from
 * this list.
 */
internal val builtinArgumentVariables = arrayOf(
    Variable("_Arg0"),
    Variable("_Arg1"),
    Variable("_Arg2"),
    Variable("_Arg3"),
    Variable("_Arg4"),
    Variable("_Arg5"),
    Variable("_Arg6"),
    Variable("_Arg7"),
    Variable("_Arg8"),
    Variable("_Arg9")
)

/**
 * This query is used as a placeholder for builtins where an instance of [Query] is required
 * but the actual query never gets invoked because kotlin code implements the builtin.
 */
private val nativeCodeQuery = PredicateInvocationQuery(CompoundTerm("__nativeCode", emptyArray()))

class NativeCodeRule(name: String, arity: Int, definedAt: StackTraceElement, code: PrologBuiltinImplementation) : Rule(
    CompoundTerm(name, builtinArgumentVariables.sliceArray(0 until arity)),
    nativeCodeQuery
), BehaviourExposingPrologCallable {
    private val invocationStackFrame = definedAt
    private val stringRepresentation = """$head :- __nativeCode("${invocationStackFrame.fileName}:${invocationStackFrame.lineNumber}")"""

    private val behaviours = mutableMapOf<DeterminismLevel, MutableList<InvocationBehaviour>>()

    private val builtinStackFrame = PrologStackTraceElement(
        head,
        invocationStackFrame.prologSourceInformation,
        null,
        "$name/$arity native implementation (${definedAt.fileName}:${definedAt.lineNumber})"
    )

    override val fulfill: suspend LazySequenceBuilder<Unification>.(Array<out Term>, ProofSearchContext) -> Unit = { arguments, context ->
        if (head.arity == arguments.size) {
            try {
                code(this, arguments, context)
            } catch (ex: PrologException) {
                ex.addPrologStackFrame(builtinStackFrame)
                throw ex
            } catch (ex: Throwable) {
                val newEx = PrologRuntimeException("Internal error", ex)
                newEx.addPrologStackFrame(builtinStackFrame)

                throw newEx
            }
        }
    }

    override fun toString() = stringRepresentation

    override fun getBehaviours(inRuntime: PrologRuntimeEnvironment, callingModule: Module, level: DeterminismLevel): List<InvocationBehaviour>? = behaviours[level]

    fun addDeterministicBehaviour(behaviour: InvocationBehaviour) {
        require(behaviour.targetCallableHead.functor == functor)
        require(behaviour.targetCallableHead.arity == arity)
        require(behaviour.outConstraints.size == 1)

        behaviours.computeIfAbsent(DeterminismLevel.DETERMINISTIC, { mutableListOf() }).also {
            it.add(behaviour)
        }
    }
}

fun nativeRule(name: String, arity: Int, code: PrologBuiltinImplementation): NativeCodeRule {
    val definedAt = getInvocationStackFrame()
    return NativeCodeRule(name, arity, definedAt, code)
}

fun nativeRule(name: String, arity: Int, definedAt: StackTraceElement, code: PrologBuiltinImplementation): NativeCodeRule {
    return NativeCodeRule(name, arity, definedAt, code)
}

fun nativeModule(name: String, initCode: NativeModuleBuilder.() -> Any?): Module = NativeModuleBuilder.build(name, initCode)

class NativeModuleBuilder(private val moduleName: String) {
    private val clauses = mutableListOf<Clause>()
    private val nativePredicates = mutableListOf<PrologCallable>()
    private val imports = mutableListOf<ModuleReference>(
        ModuleReference("library", "equality")
    )

    fun add(predicate: PrologCallable) {
        nativePredicates.add(predicate)
    }

    fun add(clauses: List<Clause>) {
        this.clauses.addAll(clauses)
    }

    fun import(pathAlias: String, moduleName: String) {
        imports.add(ModuleReference(pathAlias, moduleName))
    }

    private fun build(): Module {
        return NativeModule(
            name = moduleName,
            imports = imports.map(::FullModuleImport),
            nativePredicates = nativePredicates,
            otherClauses = clauses
        )
    }

    companion object {
        internal fun build(name: String, initCode: NativeModuleBuilder.() -> Any?): Module {
            val builder = NativeModuleBuilder(name)
            builder.initCode()
            return builder.build()
        }
    }
}

class NativeModule(
    override val name: String,
    nativePredicates: Iterable<PrologCallable>,
    otherClauses: List<Clause>,
    override val imports: List<ModuleImport>
) : Module {
    override val localOperators = ISOOpsOperatorRegistry

    override val exportedPredicates: Map<ClauseIndicator, PrologCallable>

    init {
        val _exportedPredicates = nativePredicates
            .associateBy { ClauseIndicator.of(it) }
            .toMutableMap()

        otherClauses.asSequence()
            .groupingBy { ClauseIndicator.of(it) }
            .fold(
                { indicator, _ -> ASTPrologPredicate(indicator, this) },
                { _, astPredicate, clause ->
                    astPredicate.assertz(clause)
                    astPredicate
                }
            )
            .forEach { (indicator, predicate) ->
                require(indicator !in _exportedPredicates)

                _exportedPredicates[indicator] = predicate
            }

        exportedPredicates = _exportedPredicates
    }

    override fun deriveScopedProofSearchContext(deriveFrom: ProofSearchContext): ProofSearchContext {
        if (deriveFrom is ModuleScopeProofSearchContext && deriveFrom.module == this) {
            return deriveFrom
        }

        return super.deriveScopedProofSearchContext(deriveFrom)
    }

    override fun createProofSearchContext(principal: Principal, randomVariableScope: RandomVariableScope, authorization: Authorization, runtime: PrologRuntimeEnvironment): ProofSearchContext {
        return ModuleScopeProofSearchContext(
            this,
            exportedPredicates,
            principal,
            randomVariableScope,
            authorization,
            runtime
        )
    }

    private val importLookupCaches = Collections.synchronizedMap(WeakHashMap<PrologRuntimeEnvironment, Map<ClauseIndicator, Pair<ModuleReference, PrologCallable>>>())

    override fun resolveCallable(runtime: PrologRuntimeEnvironment, simpleIndicator: ClauseIndicator): Pair<FullyQualifiedClauseIndicator, PrologCallable>? {
        // attempt modules own scope
        exportedPredicates[simpleIndicator]?.let { callable ->
            val fqIndicator = FullyQualifiedClauseIndicator(name, simpleIndicator)
            return Pair(fqIndicator, callable)
        }

        // attempt imported predicate
        val importLookupCache = importLookupCaches.computeIfAbsent(runtime) {
            Module.buildImportLookupCache(this, runtime)
        }

        importLookupCache[simpleIndicator]?.let { (sourceModule, callable) ->
            val fqIndicator = FullyQualifiedClauseIndicator(sourceModule.moduleName, ClauseIndicator.of(callable))

            return Pair(fqIndicator, callable)
        }

        return null
    }
}
