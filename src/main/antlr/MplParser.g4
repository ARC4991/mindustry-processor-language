parser grammar MplParser;

options { tokenVocab = MplLexer; }

@header { package com.arc.mpl.syntax; }

program: statement* EOF;

statement
    : variableDeclaration SEMICOLON
    | expression SEMICOLON
    ;

variableDeclaration
    : kind=(VAR | VAL) name=IDENTIFIER (COLON typeName=IDENTIFIER)? ASSIGN expression
    ;

expression: assignmentExpression;

assignmentExpression
    : IDENTIFIER operator=(ASSIGN | PLUS_ASSIGN | MINUS_ASSIGN | STAR_ASSIGN | SLASH_ASSIGN | PERCENT_ASSIGN) assignmentExpression
    | logicalOrExpression
    ;

logicalOrExpression: logicalAndExpression (OR_OR logicalAndExpression)*;
logicalAndExpression: equalityExpression (AND_AND equalityExpression)*;
equalityExpression: comparisonExpression ((EQUAL_EQUAL | BANG_EQUAL) comparisonExpression)*;
comparisonExpression: additiveExpression ((LESS | LESS_EQUAL | GREATER | GREATER_EQUAL) additiveExpression)*;
additiveExpression: multiplicativeExpression ((PLUS | MINUS) multiplicativeExpression)*;
multiplicativeExpression: unaryExpression ((STAR | SLASH | PERCENT) unaryExpression)*;

unaryExpression
    : operator=(PLUS | MINUS | BANG) unaryExpression
    | primaryExpression
    ;

primaryExpression
    : INT_LITERAL
    | FLOAT_LITERAL
    | STRING_LITERAL
    | TRUE
    | FALSE
    | target=IDENTIFIER DOT method=IDENTIFIER LPAREN (expression (COMMA expression)*)? RPAREN
    | name=IDENTIFIER
    | LPAREN grouped=expression RPAREN
    ;
