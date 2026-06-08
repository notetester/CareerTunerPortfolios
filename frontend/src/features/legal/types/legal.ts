export type LegalBlock =
  | { t: "p"; text: string }
  | { t: "ol"; items: string[] }
  | { t: "dl"; items: { term: string; desc: string }[] }
  | { t: "note"; text: string };

export interface LegalSection {
  id: string;
  num: string;
  title: string;
  blocks: LegalBlock[];
}

export interface LegalDocument {
  title: string;
  effective: string;
  version: string;
  updated: string;
  intro?: string;
  sections: LegalSection[];
}
