export type EnviableConstructor<T extends Enviable> = new () => T;

export abstract class Enviable {
  abstract toJson(): string;

  abstract fromJson(json: string): void;
}
