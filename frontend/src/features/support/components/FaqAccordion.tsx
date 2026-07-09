import {
  Accordion,
  AccordionItem,
  AccordionTrigger,
  AccordionContent,
} from "@/app/components/ui/accordion";
import type { Faq } from "../types/support";
// 공통 리치텍스트 유틸: HTML 감지 + sanitize
import { isHtmlContent, sanitizePostHtml } from "@/app/lib/postContent";
import "./faq-answer.css";

interface FaqAccordionProps {
  items: Faq[];
}

export default function FaqAccordion({ items }: FaqAccordionProps) {
  if (items.length === 0) {
    return (
      <p className="py-12 text-center text-muted-foreground">
        해당 카테고리에 등록된 FAQ가 없습니다.
      </p>
    );
  }

  return (
    <Accordion type="single" collapsible className="w-full">
      {items.map((faq) => (
        <AccordionItem key={faq.id} value={`faq-${faq.id}`}>
          <AccordionTrigger className="text-left text-base">
            <span>
              <span className="mr-2 text-[var(--brand-blue)] font-semibold">Q.</span>
              {faq.question}
            </span>
          </AccordionTrigger>
          <AccordionContent className="text-muted-foreground leading-relaxed">
            {/* HTML 답변(TipTap)은 sanitize 후 렌더, 기존 평문 답변은 그대로 — 무회귀 공존 */}
            {isHtmlContent(faq.answer) ? (
              <div
                className="faq-answer-html"
                dangerouslySetInnerHTML={{ __html: sanitizePostHtml(faq.answer) }}
              />
            ) : (
              faq.answer
            )}
          </AccordionContent>
        </AccordionItem>
      ))}
    </Accordion>
  );
}
