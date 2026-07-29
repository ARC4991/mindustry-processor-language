parser grammar MilParser;

options { tokenVocab = MilLexer; }

@header { package com.arc.mpl.mil.syntax; }

program: (functionDeclaration | statement)* EOF;

functionDeclaration: FUN name=IDENTIFIER LPAREN (parameter (COMMA parameter)*)? RPAREN
    (COLON returnType=typeReference)? body=block;

parameter: name=IDENTIFIER COLON typeName=typeReference;

typeReference: typeAtom (LBRACK RBRACK)* QUESTION?;

typeAtom
    : IDENTIFIER LESS typeReference GREATER
    | LPAREN typeReference (COMMA typeReference)+ RPAREN
    | IDENTIFIER
    ;

statement
    : macroBlockStatement
    | whileStatement
    | doWhileStatement
    | ifStatement
    | forStatement
    | forEachStatement
    | block
    | BREAK SEMICOLON
    | CONTINUE SEMICOLON
    | RETURN expression? SEMICOLON
    | variableDeclaration SEMICOLON
    | expression SEMICOLON
    ;

macroBlockStatement: macroInvocation block;
block: LBRACE statement* RBRACE;
whileStatement: WHILE LPAREN expression RPAREN block;
doWhileStatement: DO block WHILE LPAREN expression RPAREN SEMICOLON;
ifStatement: IF LPAREN expression RPAREN block (ELSE (block | ifStatement))?;
forStatement: FOR LPAREN (variableDeclaration | expression)? SEMICOLON expression? SEMICOLON expression? RPAREN block;
forEachStatement: FOR LPAREN VAR IDENTIFIER COLON expression RPAREN block;
variableDeclaration: (VAR | VAL) IDENTIFIER (COLON typeReference)? ASSIGN expression;

expression: lambdaExpression | assignmentExpression;
lambdaExpression: IDENTIFIER ARROW expression;
assignmentExpression
    : IDENTIFIER (ASSIGN | PLUS_ASSIGN | MINUS_ASSIGN | STAR_ASSIGN | SLASH_ASSIGN | PERCENT_ASSIGN) assignmentExpression
    | logicalOrExpression
    ;
logicalOrExpression: logicalAndExpression (OR_OR logicalAndExpression)*;
logicalAndExpression: equalityExpression (AND_AND equalityExpression)*;
equalityExpression: comparisonExpression ((EQUAL_EQUAL | BANG_EQUAL) comparisonExpression)*;
comparisonExpression: additiveExpression ((LESS | LESS_EQUAL | GREATER | GREATER_EQUAL) additiveExpression)*;
additiveExpression: multiplicativeExpression ((PLUS | MINUS) multiplicativeExpression)*;
multiplicativeExpression: unaryExpression ((STAR | SLASH | PERCENT) unaryExpression)*;
unaryExpression: (PLUS | MINUS | BANG) unaryExpression | postfixExpression;
postfixExpression: primaryExpression postfixSuffix*;
postfixSuffix
    : DOT IDENTIFIER
    | LPAREN (expression (COMMA expression)*)? RPAREN
    | LBRACK expression RBRACK
    ;

primaryExpression
    : INT_LITERAL
    | FLOAT_LITERAL
    | STRING_LITERAL
    | TRUE
    | FALSE
    | NULL
    | IDENTIFIER
    | macroInvocation
    | gameSymbol
    | LBRACK (expression (COMMA expression)*)? RBRACK
    | LPAREN expression (COMMA expression)+ RPAREN
    | LPAREN expression RPAREN
    ;

gameSymbol: AT IDENTIFIER;
macroInvocation: macroName LPAREN (expression (COMMA expression)*)? RPAREN;
macroName: AT IDENTIFIER (DOT IDENTIFIER)+;
