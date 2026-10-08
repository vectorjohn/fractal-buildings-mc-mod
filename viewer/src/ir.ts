// PROVISIONAL IR type. Item 0.4 defines the smoke-path IR JSON; Phase 3 the real one.
export interface IrBox {
  /** Min corner, in blocks. */
  min: [number, number, number];
  /** Size in blocks. */
  size: [number, number, number];
  /** Abstract material role (wall, trim, roof, floor, ...). */
  role: string;
}

export interface Ir {
  boxes: IrBox[];
}

export interface Voxel {
  x: number;
  y: number;
  z: number;
  role: string;
}
