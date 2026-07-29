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
    | RETURN returnValue=expression? SEMICOLON
    | variableDeclaration SEMICOLON
    | expression SEMICOLON
    ;

macroBlockStatement: macroInvocation block;
block: LBRACE statement* RBRACE;
whileStatement: WHILE LPAREN condition=expression RPAREN body=block;
doWhileStatement: DO body=block WHILE LPAREN condition=expression RPAREN SEMICOLON;
ifStatement: IF LPAREN condition=expression RPAREN thenBlock=block
    (ELSE (elseBlock=block | elseIf=ifStatement))?;
forStatement: FOR LPAREN
    (initializerDeclaration=variableDeclaration | initializerExpression=expression)? SEMICOLON
    condition=expression? SEMICOLON update=expression? RPAREN body=block;
forEachStatement: FOR LPAREN VAR name=IDENTIFIER COLON iterable=expression RPAREN body=block;
variableDeclaration: kind=(VAR | VAL) name=IDENTIFIER (COLON typeName=typeReference)? ASSIGN expression;

expression: lambdaExpression | assignmentExpression;
lambdaExpression: lambdaParameter=IDENTIFIER ARROW body=expression;
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
unaryExpression: operator=(PLUS | MINUS | BANG) unaryExpression | postfixExpression;
postfixExpression: primaryExpression postfixSuffix*;
postfixSuffix
    : DOT member=IDENTIFIER
    | LPAREN (expression (COMMA expression)*)? RPAREN
    | LBRACK index=expression RBRACK
    ;

primaryExpression
    : INT_LITERAL
    | FLOAT_LITERAL
    | STRING_LITERAL
    | TRUE
    | FALSE
    | NULL
    | name=IDENTIFIER
    | macroInvocation
    | gameSymbol
    | LBRACK (expression (COMMA expression)*)? RBRACK
    | LPAREN tupleElement+=expression (COMMA tupleElement+=expression)+ RPAREN
    | LPAREN grouped=expression RPAREN
    ;

gameSymbol: AT IDENTIFIER;
macroInvocation: macroName LPAREN (expression (COMMA expression)*)? RPAREN;
macroName: AT IDENTIFIER (DOT IDENTIFIER)+;
