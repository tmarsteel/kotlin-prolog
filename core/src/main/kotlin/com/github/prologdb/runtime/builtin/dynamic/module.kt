package com.github.prologdb.runtime.builtin.dynamic

import com.github.prologdb.runtime.builtin.nativeModule
import com.github.prologdb.runtime.query.AndQuery
import com.github.prologdb.runtime.query.OrQuery
import com.github.prologdb.runtime.query.PredicateInvocationQuery
import com.github.prologdb.runtime.query.Query
import com.github.prologdb.runtime.term.CompoundTerm

val DynamicsModule = nativeModule("dynamics") {
    add(BuiltinFindAll)
    add(BuiltinFindAllOptimized)
}

/**
 * Converts compound terms (instances of `,/2` and `;/2` to
 * queries).
 */
fun compoundToQuery(compoundTerm: CompoundTerm): Query {
    if (compoundTerm.arity != 2) {
        return PredicateInvocationQuery(compoundTerm)
    }

    if (compoundTerm.functor == "," || compoundTerm.functor == ";") {
        val allArgumentsCompound = compoundTerm.arguments.all { it is CompoundTerm }
        if (!allArgumentsCompound) {
            return PredicateInvocationQuery(compoundTerm)
        }

        val argumentsConverted = compoundTerm.arguments.map { compoundToQuery(it as CompoundTerm) }.toTypedArray()
        return when (compoundTerm.functor) {
            "," -> AndQuery(argumentsConverted)
            ";" -> OrQuery(argumentsConverted)
            else -> throw IllegalStateException()
        }
    }
    // else:
    return PredicateInvocationQuery(compoundTerm)
}
