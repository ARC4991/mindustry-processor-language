lexer grammar MplLexer;

@header { package com.arc.mpl.syntax; }

VAR: 'var';
VAL: 'val';
TRUE: 'true';
FALSE: 'false';

PLUS_ASSIGN: '+=';
MINUS_ASSIGN: '-=';
STAR_ASSIGN: '*=';
SLASH_ASSIGN: '/=';
PERCENT_ASSIGN: '%=';
EQUAL_EQUAL: '==';
BANG_EQUAL: '!=';
LESS_EQUAL: '<=';
GREATER_EQUAL: '>=';
AND_AND: '&&';
OR_OR: '||';
ASSIGN: '=';
PLUS: '+';
MINUS: '-';
STAR: '*';
SLASH: '/';
PERCENT: '%';
BANG: '!';
LESS: '<';
GREATER: '>';
LPAREN: '(';
RPAREN: ')';
COLON: ':';
COMMA: ',';
DOT: '.';
SEMICOLON: ';';

STRING_LITERAL: '"' (ESCAPE_SEQUENCE | ~["\\\r\n])* '"';
FLOAT_LITERAL: (DIGITS '.' DIGITS? | '.' DIGITS) EXPONENT?;
INT_LITERAL: DIGITS;
IDENTIFIER: [a-zA-Z_] [a-zA-Z_0-9]*;

LINE_COMMENT: '//' ~[\r\n]* -> skip;
BLOCK_COMMENT: '/*' .*? '*/' -> skip;
WHITESPACE: [ \t\r\n]+ -> skip;
ERROR_CHARACTER: .;

fragment DIGITS: [0-9]+;
fragment EXPONENT: [eE] [+-]? DIGITS;
fragment ESCAPE_SEQUENCE: '\\' ["\\nrt];
