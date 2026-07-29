parser grammar MplhParser;

options { tokenVocab = MplhLexer; }

@header { package com.arc.mpl.project; }

hardwareFile: declaration* EOF;

declaration
    : hardwareConstant
    ;

hardwareConstant
    : EXPORT? CONST name=IDENTIFIER COLON type=(IDENTIFIER | DISPLAY) ASSIGN LINK LPAREN alias=STRING_LITERAL RPAREN SEMICOLON
    | EXPORT? CONST name=IDENTIFIER COLON type=(IDENTIFIER | DISPLAY) ASSIGN DISPLAY DOT COMBINE LPAREN displayMatrix RPAREN SEMICOLON
    ;

displayRow: LBRACKET IDENTIFIER (COMMA IDENTIFIER)* RBRACKET;
displayMatrix: LBRACKET displayRow (COMMA displayRow)* RBRACKET;
