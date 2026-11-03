package com.bugmonster.analysis;

import com.github.javaparser.ast.stmt.Statement;

final class EmptyStatements {
    private EmptyStatements() {}
    static boolean isEmpty(Statement statement) {
        return statement.isEmptyStmt() || (statement.isBlockStmt()
                && statement.asBlockStmt().getStatements().stream().allMatch(EmptyStatements::isEmpty));
    }
}
