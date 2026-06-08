import {
  Accordion,
  AccordionItem,
  AccordionTrigger,
  AccordionContent,
} from "@/app/components/ui/accordion";
import type { Faq } from "../types/support";

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
            {faq.answer}
          </AccordionContent>
        </AccordionItem>
      ))}
    </Accordion>
  );
}
