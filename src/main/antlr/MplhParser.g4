parser grammar MplhParser;

options { tokenVocab = MplhLexer; }

@header { package com.arc.mpl.project; }

hardwareFile: declaration* EOF;

declaration
    : hardwareConstant
    | hardwareRequirement
    ;

hardwareConstant
    : EXPORT? CONST name=IDENTIFIER COLON type=(IDENTIFIER | DISPLAY) ASSIGN LINK LPAREN alias=STRING_LITERAL
      (COMMA linkArgument (COMMA linkArgument)*)? RPAREN SEMICOLON
    | EXPORT? CONST name=IDENTIFIER COLON type=(IDENTIFIER | DISPLAY) ASSIGN DISPLAY DOT COMBINE LPAREN displayMatrix RPAREN SEMICOLON
    ;

displayRow: LBRACKET IDENTIFIER (COMMA IDENTIFIER)* RBRACKET;
displayMatrix: LBRACKET displayRow (COMMA displayRow)* RBRACKET;
linkArgument: name=IDENTIFIER COLON value=INT_LITERAL;

hardwareRequirement
    : REQUIRE name=IDENTIFIER COLON type=(IDENTIFIER | DISPLAY) LPAREN
      (requirementArgument (COMMA requirementArgument)*)? RPAREN SEMICOLON
    ;

requirementArgument: name=IDENTIFIER COLON value=(IDENTIFIER | INT_LITERAL);
