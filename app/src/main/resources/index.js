const [, , loader, code] = process.argv;

const transpiler = new Bun.Transpiler({ loader, platform: "browser" });

try {
  const transformed = await transpiler.transform(code);
  await Bun.write(Bun.stdout, transformed);

} catch (error) {
  await Bun.write(Bun.stderr, error.message);
}
