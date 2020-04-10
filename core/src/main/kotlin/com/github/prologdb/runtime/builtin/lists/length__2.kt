package com.github.prologdb.runtime.builtin.lists

import com.github.prologdb.runtime.PrologRuntimeException
import com.github.prologdb.runtime.analyzation.constraint.InvocationBehaviour
import com.github.prologdb.runtime.analyzation.constraint.TypeTermConstraint
import com.github.prologdb.runtime.builtin.builtinArgumentVariables
import com.github.prologdb.runtime.builtin.nativeRule
import com.github.prologdb.runtime.term.CompoundTerm
import com.github.prologdb.runtime.term.PrologInteger
import com.github.prologdb.runtime.term.PrologList
import com.github.prologdb.runtime.term.Variable

/**
 * length(++List, :Length)
 */
internal val LengthBuiltin = nativeRule("length", 2) { args, ctxt ->
    val arg0 = args[0]
    val arg1 = args[1]

    if (arg0 is PrologList) {
        val length = PrologInteger.createUsingStringOptimizerCache(arg0.elements.size.toLong())
        length.unify(arg1, ctxt.randomVariableScope)?.let { yield(it) }
    }
    else if (arg0 is Variable) {
        if (arg1 !is PrologInteger) {
            throw PrologRuntimeException("If argument 1 to length/2 is a variable, argument 2 must be an integer (got ${arg1.prologTypeName})")
        }

        for (size in 0..arg1.toInteger()) {
            val elements = List(size.toInt()) { ctxt.randomVariableScope.createNewRandomVariable() }
            PrologList(elements, null).unify(arg0, ctxt.randomVariableScope)?.let { yield(it) }
        }
    }
}.apply {
    addDeterministicBehaviour(
        InvocationBehaviour(
            CompoundTerm("length", arrayOf(
                builtinArgumentVariables[0],
                builtinArgumentVariables[1]
            )),
            mapOf(
                builtinArgumentVariables[0] to TypeTermConstraint<PrologList>(),
                builtinArgumentVariables[1] to TypeTermConstraint<Variable>()
            ),
            listOf(
                mapOf(
                    builtinArgumentVariables[1] to TypeTermConstraint<PrologInteger>()
                )
            )
        )
    )
}
