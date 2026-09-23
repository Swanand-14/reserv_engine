// The backend carries no poster image for an Event — only a title. Rather
// than fake a photo, we derive a stable abstract "poster" gradient from the
// title itself: same title always produces the same art, different titles
// spread out across the palette. This is a design choice, not a placeholder.

function hashString(input: string): number {
  let hash = 5381;
  for (let i = 0; i < input.length; i++) {
    hash = (hash * 33) ^ input.charCodeAt(i);
  }
  return Math.abs(hash);
}

export function posterArtFor(title: string): { hue: number; hueAlt: number } {
  const hash = hashString(title);
  const hue = hash % 360;
  // A near-complementary second stop, nudged so the gradient never looks flat.
  const hueAlt = (hue + 55 + (hash % 40)) % 360;
  return { hue, hueAlt };
}
