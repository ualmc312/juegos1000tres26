export type EnviableConstructor<T extends Enviable> = new () => T;

export abstract class Enviable {
  abstract out(): unknown;

  abstract in(entrada: unknown): void;
}
