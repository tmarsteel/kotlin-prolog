package com.github.tmarsteel.ktprolog

import com.github.tmarsteel.ktprolog.term.Term
import com.github.tmarsteel.ktprolog.term.Variable

/**
 * Handles the remapping of user-specified variables to random variables (that avoids name collisions).
 */
class RandomVariableScope {
    /**
     * This is essentially the most simple PRNG possible: this int will just be incremented until it hits Long.MAX_VALUE
     * and will then throw an exception. That number is then used to generate variable names.
     */
    private var randomCounter: Long = 0

    /**
     * Replaces all the variables in the given predicate with random instances; the mapping gets stored
     * in the given map.
     */
    fun withRandomVariables(term: Term, mapping: VariableMapping): Term {
        return term.substituteVariables { originalVariable ->
            if (originalVariable == Variable.ANONYMOUS) {
                createNewRandomVariable()
            }
            else {
                if (!mapping.hasOriginal(originalVariable)) {
                    val randomVariable = createNewRandomVariable()
                    mapping.storeSubstitution(originalVariable, randomVariable)
                }

                mapping.getSubstitution(originalVariable)!!
            }
        }
    }

    fun createNewRandomVariable(): Variable {
        if (randomCounter == Long.MAX_VALUE) {
            throw PrologRuntimeException("Out of random variables")
        }
        
        return RandomVariable(randomCounter++)
    }
}
