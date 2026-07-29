lexer grammar MplhLexer;

@header { package com.arc.mpl.project; }

EXPORT: 'export';
CONST: 'const';
REQUIRE: 'require';
MEMORY: 'memory';
PHYSICAL: 'physical';
VIRTUAL: 'virtual';
LINK: 'link';
SIZE: 'size';
MODE: 'mode';
POOL: 'Pool';
STATIC: 'Static';
DISPLAY: 'Display';
COMBINE: 'combine';

LPAREN: '(';
RPAREN: ')';
LBRACKET: '[';
RBRACKET: ']';
COLON: ':';
COMMA: ',';
DOT: '.';
ASSIGN: '=';
SEMICOLON: ';';

STRING_LITERAL: '"' (ESCAPE_SEQUENCE | ~["\\\r\n])* '"';
INT_LITERAL: [0-9]+;
IDENTIFIER: [a-zA-Z_] [a-zA-Z_0-9]*;

LINE_COMMENT: '//' ~[\r\n]* -> skip;
BLOCK_COMMENT: '/*' .*? '*/' -> skip;
WHITESPACE: [ \t\r\n]+ -> skip;
ERROR_CHARACTER: .;

fragment ESCAPE_SEQUENCE: '\\' ["\\nrt];
