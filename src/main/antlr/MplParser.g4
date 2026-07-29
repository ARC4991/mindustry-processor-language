parser grammar MplParser;

options { tokenVocab = MplLexer; }

@header { package com.arc.mpl.syntax; }

program: statement* EOF;

statement
    : whileStatement
    | doWhileStatement
    | ifStatement
    | forStatement
    | forEachStatement
    | block
    | BREAK SEMICOLON
    | CONTINUE SEMICOLON
    | variableDeclaration SEMICOLON
    | expression SEMICOLON
    ;

block: LBRACE statement* RBRACE;

whileStatement: WHILE LPAREN condition=expression RPAREN body=block;

doWhileStatement: DO body=block WHILE LPAREN condition=expression RPAREN SEMICOLON;

ifStatement: IF LPAREN condition=expression RPAREN thenBlock=block
    (ELSE (elseBlock=block | elseIf=ifStatement))?;

forStatement: FOR LPAREN
    (initializerDeclaration=variableDeclaration | initializerExpression=expression)? SEMICOLON
    condition=expression? SEMICOLON update=expression? RPAREN body=block;

forEachStatement: FOR LPAREN VAR name=IDENTIFIER COLON iterable=expression RPAREN body=block;

variableDeclaration
    : kind=(VAR | VAL) name=IDENTIFIER (COLON typeName=IDENTIFIER)? ASSIGN expression
    ;

expression
    : lambdaExpression
    | assignmentExpression
    ;

lambdaExpression: parameter=IDENTIFIER ARROW body=expression;

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
    | postfixExpression
    ;

postfixExpression: primaryExpression postfixSuffix*;

postfixSuffix
    : DOT member=IDENTIFIER
    | LPAREN (expression (COMMA expression)*)? RPAREN
    ;

primaryExpression
    : INT_LITERAL
    | FLOAT_LITERAL
    | STRING_LITERAL
    | TRUE
    | FALSE
    | name=IDENTIFIER
    | LPAREN grouped=expression RPAREN
    ;
