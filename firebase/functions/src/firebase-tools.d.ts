declare module "firebase-tools" {
  export const firestore: {
    delete(
      path: string,
      options: { project?: string | undefined; recursive?: boolean | undefined; yes?: boolean | undefined },
    ): Promise<void>;
  };
}
