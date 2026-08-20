/**
 * One-shot Playwright capture for README screenshots. Free Chromium download, no package.json dep.
 * Usage (API on 8080): node scripts/capture-screenshots.mjs
 */
import { mkdirSync } from "node:fs";
import { createRequire } from "node:module";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const root = join(dirname(fileURLToPath(import.meta.url)), "..");
const { chromium } = createRequire(join(root, "frontend", "package.json"))("playwright");
const outDir = join(root, "docs", "screenshots");
const base = process.env.CAREFLOW_URL ?? "http://127.0.0.1:8080";

mkdirSync(outDir, { recursive: true });

const browser = await chromium.launch();
const page = await browser.newPage({ viewport: { width: 1440, height: 900 } });

async function shot(name) {
  await page.screenshot({ path: join(outDir, name), fullPage: false });
}

await page.goto(base, { waitUntil: "domcontentloaded" });
await page.waitForSelector(".login-card");
await shot("01-login.png");

await page.getByRole("button", { name: /Lena Weber/ }).click();
await page.waitForSelector(".beds");
await shot("02-station.png");

await page.locator(".bed.star").click();
await page.waitForSelector("h2");
await shot("03-akte-elena.png");

await page.getByRole("button", { name: "Blutbild + CRP" }).click();
await page.waitForTimeout(800);
await page.getByRole("navigation").getByRole("button", { name: "Labor" }).click();
await page.waitForSelector("h2");
await shot("04-labor.png");

await page.locator("tr.demo-row").getByRole("button", { name: "Befund freigeben" }).click();
await page.waitForTimeout(1200);
await page.getByRole("button", { name: "Amoxicillin — Allergie-Check" }).click();
await page.waitForTimeout(800);
await shot("05-amts-sperre.png");

await page.getByRole("button", { name: "Stattdessen Cefuroxim (J01D)" }).click();
await page.waitForTimeout(800);
await page.getByRole("navigation").getByRole("button", { name: "HL7 / FHIR" }).click();
await page.waitForTimeout(600);
await shot("06-interop.png");

await browser.close();
console.log("Wrote screenshots to", outDir);
