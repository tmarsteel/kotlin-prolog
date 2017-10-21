package com.github.tmarsteel.ktprolog.term

import com.github.tmarsteel.ktprolog.knowledge.RandomVariableScope
import com.github.tmarsteel.ktprolog.unification.Unification
import com.github.tmarsteel.ktprolog.unification.VariableBucket
import java.util.*

open class Predicate(val name: String, val arguments: Array<out Term>) : Term
{
    override fun unify(rhs: Term, randomVarsScope: RandomVariableScope): Unification? {
        if (rhs is Predicate) {
            if (this.name != rhs.name) {
                return Unification.FALSE
            }

            if (this.arguments.size != rhs.arguments.size) {
                return Unification.FALSE
            }

            if (arguments.isEmpty()) {
                return Unification.TRUE
            }

            val vars = VariableBucket()
            for (argIndex in 0..arguments.lastIndex) {
                val lhsArg = arguments[argIndex].substituteVariables(vars.asSubstitutionMapper())
                val rhsArg = rhs.arguments[argIndex].substituteVariables(vars.asSubstitutionMapper())
                val argUnification = lhsArg.unify(rhsArg, randomVarsScope)

                if (argUnification == null) {
                    // the arguments at place argIndex do not unify => the predicates don't unify
                    return Unification.FALSE
                }

                for ((variable, value) in argUnification.variableValues.values) {
                    if (!vars.isDefined(variable)) {
                        vars.define(variable)
                    }

                    if (value.isPresent) {
                        // substitute all instantiated variables for simplicity and performance
                        val substitutedValue = value.get()!!.substituteVariables(vars.asSubstitutionMapper())
                        if (vars.isInstantiated(variable)) {
                            if (vars[variable] != substitutedValue && vars[variable] != value) {
                                // instantiated to different value => no unification
                                return Unification.FALSE
                            }
                        }
                        else {
                            vars.instantiate(variable, substitutedValue)
                        }
                    }
                }
            }

            // we made it through all arguments without issues => great
            return Unification(vars)
        }
        else if (rhs is Variable) {
            return rhs.unify(this)
        }
        else
        {
            return Unification.FALSE
        }
    }

    override val variables = arguments.flatMap(Term::variables).toSet()

    override fun substituteVariables(mapper: (Variable) -> Term): Predicate {
        return Predicate(name, arguments.map { it.substituteVariables(mapper) }.toTypedArray())
    }

    override fun toString(): String {
        return name + "(" + arguments.joinToString(",") + ")"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Predicate

        if (name != other.name) return false
        if (!Arrays.equals(arguments, other.arguments)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + Arrays.hashCode(arguments)
        return result
    }
}

class PredicateBuilder(private val predicateName: String) {
    operator fun invoke(vararg arguments: Term) = Predicate(predicateName, arguments)
}