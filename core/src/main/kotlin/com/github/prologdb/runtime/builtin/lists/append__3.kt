package com.github.prologdb.runtime.builtin.lists

import com.github.prologdb.runtime.builtin.A
import com.github.prologdb.runtime.builtin.B
import com.github.prologdb.runtime.builtin.C
import com.github.prologdb.runtime.builtin.X
import com.github.prologdb.runtime.knowledge.Rule
import com.github.prologdb.runtime.knowledge.library.LibraryEntry
import com.github.prologdb.runtime.query.PredicateQuery
import com.github.prologdb.runtime.term.Predicate
import com.github.prologdb.runtime.term.List as PrologList

/**
 * Implements the append/3 builtin:
 *
 *     append([], L, L).
 *     append([H|T], L2, [H|R]) :- append(T, L2, R).
 */
internal val AppendBuiltin = listOf<LibraryEntry>(
    // append([], L, L) :- list(L).
    Predicate("append", arrayOf(
        PrologList(emptyList()),
        X,
        X
    )),
    // append([H|T], L2, [H|R]) :- list(L2), append(T, L2, R).
    Rule(
        Predicate("append", arrayOf(
            PrologList(listOf(A), X),
            B,
            PrologList(listOf(A), C)
        )),
        PredicateQuery(Predicate("append", arrayOf(X, B, C)))
    )
)