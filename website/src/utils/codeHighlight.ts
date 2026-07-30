import type { CodeLanguage } from "../types/tutorial";

export type TokenKind = "plain" | "comment" | "keyword" | "type" | "function" | "string" | "number" | "literal" | "macro";

export interface CodeToken {
  text: string;
  kind: TokenKind;
}

const languageKeywords = new Set([
  "var", "val", "fun", "class", "extends", "public", "private", "new", "this", "super",
  "for", "while", "do", "if", "else", "break", "continue", "return", "import", "export",
  "from", "with", "const", "require", "memory", "physical", "virtual", "link", "combine"
]);

const literalKeywords = new Set(["true", "false", "null"]);
const builtinTypes = new Set([
  "Int", "Float", "Bool", "String", "Void", "Unit", "Building", "List", "Set", "MutableList",
  "Display", "Message", "Color", "Pool", "Static"
]);
const shellCommands = new Set(["mpl", "init", "install", "search", "check", "build"]);

const tokenPattern = /\/\/[^\n]*|\/\*[\s\S]*?\*\/|"(?:\\.|[^"\\])*"|@[A-Za-z_][A-Za-z_0-9]*(?:\.[A-Za-z_][A-Za-z_0-9]*)*|--[A-Za-z][A-Za-z0-9-]*(?:=[^\s]+)?|\b\d+(?:\.\d+)?\b|\b[A-Za-z_][A-Za-z_0-9]*\b|[\s\S]/g;

export function highlightCode(source: string, language: CodeLanguage): CodeToken[] {
  const tokens: CodeToken[] = [];
  for (const match of source.matchAll(tokenPattern)) {
    const text = match[0];
    const end = (match.index ?? 0) + text.length;
    const remainder = source.slice(end);
    let kind: TokenKind = "plain";

    if (text.startsWith("//") || text.startsWith("/*")) kind = "comment";
    else if (text.startsWith('"')) kind = "string";
    else if (text.startsWith("@")) kind = "macro";
    else if (/^\d/.test(text)) kind = "number";
    else if (literalKeywords.has(text)) kind = "literal";
    else if (language === "shell" && (shellCommands.has(text) || text.startsWith("--"))) kind = "keyword";
    else if (languageKeywords.has(text)) kind = "keyword";
    else if (builtinTypes.has(text) || /^[A-Z][A-Za-z_0-9]*$/.test(text)) kind = "type";
    else if (/^\s*\(/.test(remainder)) kind = "function";

    const previous = tokens[tokens.length - 1];
    if (previous?.kind === kind) previous.text += text;
    else tokens.push({ text, kind });
  }
  return tokens;
}
