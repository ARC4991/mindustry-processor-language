export type TutorialTrack = "mpl" | "mil";
export type CodeLanguage = "mpl" | "mil" | "mplh" | "json" | "shell";

export interface TutorialCode {
  language: CodeLanguage;
  title?: string;
  source: string;
}

export interface TutorialSection {
  title: string;
  paragraphs: string[];
  terms?: string[];
  code?: TutorialCode;
  callout?: {
    tone: "info" | "warning";
    title: string;
    text: string;
  };
}

export interface TutorialLesson {
  id: string;
  track: TutorialTrack;
  number: string;
  title: string;
  summary: string;
  keywords: string[];
  sections: TutorialSection[];
}
