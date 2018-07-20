package com.github.prologdb.runtime

import com.github.prologdb.runtime.term.Predicate

/**
 * An exception related to, but not limited to, parsing and interpreting prolog programs.
 */
open class PrologException(message: String, override val cause: Throwable? = null) : RuntimeException(message)

/**
 * Thrown when errors or warnings occur during the interpretation of a prolog program.
 */
open class PrologRuntimeException(message: String, cause: Throwable? = null) : PrologException(message, cause)

/**
 * Thrown when a prolog system is asked to handle a directive that is not supported. Directives are instances of
 * `:-/1`; Usually, attempting to handle other predicates as directives leads to this error.
 */
class IllegalDirectiveException(message: String, cause: Throwable? = null) : PrologException(message, cause) {
    constructor(rejectedDirective: Predicate) : this(
            if (rejectedDirective.name != ":-" || rejectedDirective.arity != 1) {
                "Directives must be instances of :-/1"
            } else {
                "Unsupported or illegal directive: $rejectedDirective"
            }
    )
}

data class PrologStackTraceElement(
    val goalPredicate: Predicate,
    val sourceInformation: PrologSourceInformation
)