package com.github.tmarsteel.ktprolog.parser.source

/**
 * A location in a unit of source code (e.g. a source file)
 */
open class SourceLocation (
        val unit: SourceUnit,

        /** The line number, starting with 1 */
        val line: Int,

        /** The column number, starting with 1 */
        val column: Int
)