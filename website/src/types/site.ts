export type PageKey = "home" | "tutorial" | "language" | "design" | "runtime" | "packages" | "compiler" | "examples";

export interface PageContent {
  label: string;
  eyebrow: string;
  title: string;
  lead: string;
}
