import { Avatar, AvatarFallback } from "@/app/components/ui/avatar";
import {
  ArrowLeft, Eye, Clock, Star,
  Users, Layers, Calendar, Gauge,
} from "lucide-react";
import { CategoryBadge } from "./CategoryBadge";
import { ReactionButtons } from "./ReactionButtons";
import { CommentSection } from "./CommentSection";
import { mockPostDetail } from "../data/mockCommunity";

interface PostDetailViewProps {
  onBack: () => void;
}

function DifficultyStars({ level }: { level: number }) {
  return (
    <span className="ct-stars">
      {[1, 2, 3, 4, 5].map((n) => (
        <span key={n} className={n <= level ? "on" : "off"}>
          <Star />
        </span>
      ))}
    </span>
  );
}

function renderInline(text: string): React.ReactNode {
  const parts: React.ReactNode[] = [];
  const regex = /(\*\*(.+?)\*\*|`(.+?)`)/g;
  let lastIndex = 0;
  let match;
  let k = 0;
  while ((match = regex.exec(text)) !== null) {
    if (match.index > lastIndex) parts.push(text.slice(lastIndex, match.index));
    if (match[2]) parts.push(<strong key={k}>{match[2]}</strong>);
    else parts.push(<code key={k}>{match[3]}</code>);
    lastIndex = regex.lastIndex;
    k++;
  }
  if (lastIndex < text.length) parts.push(text.slice(lastIndex));
  return parts.length === 1 ? parts[0] : <>{parts}</>;
}

function renderMarkdown(md: string) {
  const lines = md.split("\n");
  const blocks: React.ReactNode[] = [];
  let i = 0;
  while (i < lines.length) {
    const line = lines[i];
    if (line.trim() === "") { i++; continue; }
    if (line.trim().startsWith("```")) {
      const code: string[] = []; i++;
      while (i < lines.length && !lines[i].trim().startsWith("```")) { code.push(lines[i]); i++; }
      i++;
      blocks.push(<pre key={`pre-${i}`}><code>{code.join("\n")}</code></pre>);
      continue;
    }
    if (line.startsWith("### ")) {
      blocks.push(<h3 key={i}>{renderInline(line.slice(4))}</h3>);
      i++; continue;
    }
    if (line.startsWith("## ")) {
      blocks.push(<h2 key={i}>{renderInline(line.slice(3))}</h2>);
      i++; continue;
    }
    if (line.startsWith("> ")) {
      const q = [line.slice(2)]; i++;
      while (i < lines.length && lines[i].startsWith("> ")) { q.push(lines[i].slice(2)); i++; }
      blocks.push(<blockquote key={`q-${i}`}>{renderInline(q.join(" "))}</blockquote>);
      continue;
    }
    if (/^[-*] /.test(line)) {
      const items: string[] = [];
      while (i < lines.length && /^[-*] /.test(lines[i])) { items.push(lines[i].replace(/^[-*] /, "")); i++; }
      blocks.push(
        <ul key={`ul-${i}`}>
          {items.map((it, x) => <li key={x}>{renderInline(it)}</li>)}
        </ul>
      );
      continue;
    }
    if (/^\d+\. /.test(line)) {
      const items: string[] = [];
      while (i < lines.length && /^\d+\. /.test(lines[i])) { items.push(lines[i].replace(/^\d+\. /, "")); i++; }
      blocks.push(
        <ol key={`ol-${i}`}>
          {items.map((it, x) => <li key={x}>{renderInline(it)}</li>)}
        </ol>
      );
      continue;
    }
    const para = [line]; i++;
    while (i < lines.length && lines[i].trim() !== "" && !/^(#|>|[-*] |\d+\. |```)/.test(lines[i])) { para.push(lines[i]); i++; }
    blocks.push(<p key={`p-${i}`}>{renderInline(para.join(" "))}</p>);
  }
  return blocks;
}

export function PostDetailView({ onBack }: PostDetailViewProps) {
  const d = mockPostDetail;
  const isInterview = d.category === "면접후기";

  return (
    <div className="ct-page ct-detail">
      {/* Back */}
      <button className="ct-detail__back" onClick={onBack}>
        <ArrowLeft /> 커뮤니티 목록
      </button>

      {/* Head */}
      <div className="ct-detail__head">
        <div className="ct-detail__tags">
          <CategoryBadge label={d.category} />
          {d.result && (
            <span className="ct-badge ct-badge--success">{d.result}</span>
          )}
        </div>
        <h1 className="ct-detail__title">{d.title}</h1>

        <div className="ct-detail__byline">
          <Avatar className="w-10 h-10">
            <AvatarFallback className="bg-muted text-sm">{d.author[0]}</AvatarFallback>
          </Avatar>
          <div className="ct-detail__who">
            <div className="ct-detail__name">
              {d.author}
              {d.authorRole && <span className="ct-detail__role">{d.authorRole}</span>}
            </div>
            <div className="ct-detail__sub">
              <span><Clock />{d.time}</span>
              <span><Eye />조회 {d.views.toLocaleString()}</span>
            </div>
          </div>
        </div>
      </div>

      <hr style={{ border: "none", borderTop: "1px solid var(--border)", margin: "20px 0" }} />

      {/* Interview meta card */}
      {isInterview && d.meta && (
        <div className="ct-imeta">
          <div className="ct-imeta__top">
            <Avatar className="w-10 h-10">
              <AvatarFallback className="text-sm font-bold">{d.meta.company[0]}</AvatarFallback>
            </Avatar>
            <div>
              <div className="ct-imeta__co">{d.meta.company}</div>
              <div className="ct-imeta__pos">{d.meta.position}</div>
            </div>
          </div>
          <div className="ct-imeta__grid">
            {[
              { icon: Users, label: "면접 유형", value: d.meta.type },
              { icon: Layers, label: "진행 전형", value: d.meta.stage },
              { icon: Calendar, label: "면접일", value: d.meta.date },
              { icon: Gauge, label: "체감 난이도", value: null, stars: d.meta.difficulty },
            ].map((cell, idx) => (
              <div key={idx} className="ct-imeta__cell">
                <div className="ct-imeta__k">
                  <cell.icon />{cell.label}
                </div>
                <div className="ct-imeta__v">
                  {cell.stars ? <DifficultyStars level={cell.stars} /> : cell.value}
                </div>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Body */}
      <div className="ct-prose">{renderMarkdown(d.body)}</div>

      {/* Action bar */}
      <ReactionButtons likes={d.likes} />

      {/* Comments */}
      <CommentSection comments={d.comments} />
    </div>
  );
}
