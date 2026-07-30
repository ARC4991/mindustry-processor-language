parser grammar MplParser;

options { tokenVocab = MplLexer; }

@header { package com.arc.mpl.syntax; }

program: importDeclaration* topLevelDeclaration* EOF;

importDeclaration: IMPORT LBRACE importedName+=IDENTIFIER (COMMA importedName+=IDENTIFIER)* RBRACE
    FROM source=STRING_LITERAL (WITH LBRACE
        (hardwareArgument (COMMA hardwareArgument)*)? RBRACE)? SEMICOLON;

hardwareArgument: name=IDENTIFIER COLON value=IDENTIFIER;

topLevelDeclaration
    : exported=EXPORT? functionDeclaration
    | exported=EXPORT? classDeclaration
    | exported=EXPORT? variableDeclaration SEMICOLON
    | statement
    ;

classDeclaration: CLASS name=IDENTIFIER (EXTENDS superName=IDENTIFIER)? LBRACE classMember* RBRACE;

classMember
    : accessModifier? fieldDeclaration
    | accessModifier? functionDeclaration
    ;

accessModifier: PUBLIC | PRIVATE;

fieldDeclaration: name=IDENTIFIER COLON typeName=typeReference SEMICOLON;

functionDeclaration: FUN name=IDENTIFIER LPAREN (parameter (COMMA parameter)*)? RPAREN
    (COLON returnType=typeReference)? body=block;

parameter: name=IDENTIFIER COLON typeName=typeReference;

typeReference
    : typeAtom (LBRACK RBRACK)* QUESTION?
    ;

typeAtom
    : IDENTIFIER LESS typeReference GREATER
    | LPAREN typeReference (COMMA typeReference)+ RPAREN
    | IDENTIFIER
    ;

statement
    : whileStatement
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
    : kind=(VAR | VAL) name=IDENTIFIER (COLON typeName=typeReference)? ASSIGN expression
    ;

expression
    : lambdaExpression
    | assignmentExpression
    ;

lambdaExpression: lambdaParameter=IDENTIFIER ARROW body=expression;

assignmentExpression
    : assignmentTarget operator=(ASSIGN | PLUS_ASSIGN | MINUS_ASSIGN | STAR_ASSIGN | SLASH_ASSIGN | PERCENT_ASSIGN) assignmentExpression
    | logicalOrExpression
    ;

assignmentTarget: object=(IDENTIFIER | THIS | SUPER) (DOT member=IDENTIFIER)?;

logicalOrExpression: logicalAndExpression (OR_OR logicalAndExpression)*;
logicalAndExpression: equalityExpression (AND_AND equalityExpression)*;
equalityExpression: comparisonExpression ((STRICT_EQUAL | STRICT_NOT_EQUAL | EQUAL_EQUAL | BANG_EQUAL) comparisonExpression)*;
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
    | LBRACK index=expression RBRACK
    ;

primaryExpression
    : NEW typeName=IDENTIFIER LPAREN (constructorArgument+=expression (COMMA constructorArgument+=expression)*)? RPAREN
    | INT_LITERAL
    | FLOAT_LITERAL
    | STRING_LITERAL
    | TRUE
    | FALSE
    | NULL
    | THIS
    | SUPER
    | name=IDENTIFIER
    | LBRACK (expression (COMMA expression)*)? RBRACK
    | LPAREN tupleElement+=expression (COMMA tupleElement+=expression)+ RPAREN
    | LPAREN grouped=expression RPAREN
    ;
