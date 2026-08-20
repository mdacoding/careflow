import type { WardCard } from "./types";

export function WardBoard({ ward, onOpen }: { ward: WardCard[]; onOpen: (id: string) => void }) {
  return (
    <>
      <section className="stats">
        <article className="stat">
          <strong>{ward.length}</strong>
          <span>Fälle Station Innere 3</span>
        </article>
        <article className="stat">
          <strong>{ward.reduce((sum, card) => sum + card.openLabs, 0)}</strong>
          <span>offene Laboraufträge</span>
        </article>
        <article className="stat">
          <strong>{ward.filter((card) => card.allergies.length).length}</strong>
          <span>mit dokumentierter Allergie</span>
        </article>
        <article className="stat">
          <strong>{ward.filter((card) => card.criticalResult).length}</strong>
          <span>pathologische Befunde</span>
        </article>
      </section>
      <section className="beds">
        {ward.map((card) => (
          <article key={card.id} className={card.demoStar ? "bed star" : "bed"} onClick={() => onOpen(card.id)}>
            <header>
              <div>
                <div className="kicker">
                  Bett {card.bed} · {card.mrn}
                </div>
                <strong>{card.displayName}</strong>
              </div>
              <div className="chips">
                {card.demoStar && <span className="chip star">Demo-Fall</span>}
                <span className={`chip ${card.acuity === "hoch" ? "high" : ""}`}>Akuität {card.acuity}</span>
              </div>
            </header>
            <div>{card.workingDiagnosis}</div>
            <div className="muted">{card.chiefComplaint}</div>
            <div className="chips">
              {card.allergies.map((allergy) => (
                <span key={allergy} className="chip high">
                  {allergy}
                </span>
              ))}
              {card.openLabs > 0 && <span className="chip warn">{card.openLabs} Labor offen</span>}
              {card.criticalResult && <span className="chip high">Befund pathologisch</span>}
            </div>
          </article>
        ))}
      </section>
    </>
  );
}
