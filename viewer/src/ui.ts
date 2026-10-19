import { ALL_LAYERS } from './state';
import type { ViewState } from './state';

/** What the panel shows; main.ts rebuilds it after every change. */
export interface UiModel {
  state: ViewState;
  styles: string[];
  /** Style in effect (state.style or the scene's own). */
  style: string;
  seeds: number[];
  topStorey: number;
  status: string;
}

export interface UiHandlers {
  onSeed(delta: number | { to: number }): void;
  onStyle(style: string): void;
  onLayer(layer: string, on: boolean): void;
  onColour(c: 'role' | 'palette'): void;
  onStoreys(n: number | null): void;
}

export interface Ui { update(m: UiModel): void }

function el<K extends keyof HTMLElementTagNameMap>(tag: K, id: string, parent: HTMLElement, text?: string): HTMLElementTagNameMap[K] {
  const e = document.createElement(tag);
  e.id = id;
  if (text !== undefined) e.textContent = text;
  parent.appendChild(e);
  return e;
}

/** Builds the control panel once; `update` only sets values, so dragging a slider is never interrupted. */
export function createUi(h: UiHandlers): Ui {
  const panel = document.createElement('div');
  panel.id = 'panel';
  panel.style.cssText = 'position:fixed;top:8px;left:8px;font:12px sans-serif;color:#ddd;background:#000a;padding:6px 10px;border-radius:4px;display:flex;flex-direction:column;gap:5px;max-width:260px';
  const row = (id: string): HTMLDivElement => {
    const r = el('div', id, panel);
    r.style.cssText = 'display:flex;gap:4px;align-items:center;flex-wrap:wrap';
    return r;
  };

  const seedRow = row('seed-row');
  seedRow.append('seed');
  const prev = el('button', 'seed-prev', seedRow, '<');
  const seedBox = el('input', 'seed', seedRow);
  seedBox.type = 'number'; seedBox.style.width = '70px';
  const next = el('button', 'seed-next', seedRow, '>');
  const slider = el('input', 'seed-slider', panel);
  slider.type = 'range';
  prev.onclick = () => h.onSeed(-1);
  next.onclick = () => h.onSeed(1);
  seedBox.onchange = () => { if (seedBox.value !== '') h.onSeed({ to: Number(seedBox.value) }); };
  let seeds: number[] = [];
  slider.oninput = () => { const s = seeds[Number(slider.value)]; if (s !== undefined) h.onSeed({ to: s }); };

  const styleRow = row('style-row');
  styleRow.append('style');
  const styleSel = el('select', 'style', styleRow);
  styleSel.onchange = () => h.onStyle(styleSel.value);
  const colourSel = el('select', 'colour', styleRow);
  for (const v of ['role', 'palette']) colourSel.add(new Option(`colour: ${v}`, v));
  colourSel.onchange = () => h.onColour(colourSel.value === 'palette' ? 'palette' : 'role');

  const layerRow = row('layers');
  const boxes = new Map<string, HTMLInputElement>();
  for (const l of ALL_LAYERS) {
    const lab = document.createElement('label');
    lab.style.marginRight = '6px';
    const cb = document.createElement('input');
    cb.type = 'checkbox'; cb.id = `layer-${l}`;
    cb.onchange = () => h.onLayer(l, cb.checked);
    lab.append(cb, l);
    layerRow.appendChild(lab);
    boxes.set(l, cb);
  }

  const storeyRow = row('storey-row');
  const storeyLabel = el('span', 'storey-label', storeyRow);
  const storey = el('input', 'storeys', storeyRow);
  storey.type = 'range'; storey.min = '0';
  let top = 0;
  storey.oninput = () => h.onStoreys(Number(storey.value) >= top ? null : Number(storey.value));

  const status = el('div', 'status', panel);
  status.style.opacity = '0.7';
  document.body.appendChild(panel);

  // Arrow keys step the seed (unless typing in a field).
  window.addEventListener('keydown', (e) => {
    const t = e.target;
    if (t instanceof HTMLInputElement && t.type !== 'range' && t.type !== 'checkbox') return;
    if (t instanceof HTMLSelectElement) return;
    if (e.key === 'ArrowLeft') h.onSeed(-1);
    else if (e.key === 'ArrowRight') h.onSeed(1);
  });

  return {
    update(m) {
      seeds = m.seeds;
      const single = m.seeds.length === 0;
      for (const c of [prev, next, seedBox, slider]) c.disabled = single;
      // Don't clobber a seed being typed because an earlier load finished.
      if (document.activeElement !== seedBox) seedBox.value = m.state.seed === null ? '' : String(m.state.seed);
      slider.min = '0'; slider.max = String(Math.max(0, m.seeds.length - 1));
      const idx = m.state.seed === null ? -1 : m.seeds.indexOf(m.state.seed);
      slider.value = String(Math.max(idx, 0));
      if (styleSel.options.length !== m.styles.length) {
        styleSel.length = 0;
        for (const s of m.styles) styleSel.add(new Option(s, s));
      }
      styleSel.value = m.style;
      colourSel.value = m.state.colour;
      for (const [l, cb] of boxes) cb.checked = m.state.layers.has(l);
      top = m.topStorey;
      storey.max = String(top);
      storey.value = String(m.state.maxStorey ?? top);
      storey.disabled = top === 0;
      storeyLabel.textContent = `storeys up to ${m.state.maxStorey ?? top}`;
      status.textContent = m.status;
    },
  };
}
