export interface QualityReport {
  sharpness: number;
  brightness: number;
  glareDetected: boolean;
  framed: boolean;
  overallOk: boolean;
}

const SHARPNESS_MIN = 12;
const BRIGHTNESS_MIN = 60;
const BRIGHTNESS_MAX = 200;
const GLARE_LUMA_THRESHOLD = 245;
const GLARE_RATIO_MAX = 0.02;
// Doit correspondre à l'inset CSS du cadre visuel (.scan-frame { inset: 10% 8% }) :
// l'image analysée doit être recadrée en amont (voir computeCoverSourceRect) pour que
// ces marges correspondent réellement au bord du cadre affiché à l'écran.
const FRAME_MARGIN_RATIO_Y = 0.1;
const FRAME_MARGIN_RATIO_X = 0.08;
const FRAME_CONTRAST_MIN = 10;
const FRAME_EDGES_REQUIRED = 2;

/**
 * Analyse une image capturée (issue d'un canvas réduit) pour estimer si elle est
 * exploitable pour une capture automatique de document : netteté (variance du
 * Laplacien), luminosité (luma moyenne), reflet (ratio de pixels quasi-blancs) et
 * cadrage (contraste détecté le long du contour du cadre de capture).
 */
export function analyzeImageQuality(imageData: ImageData): QualityReport {
  const luma = toLuma(imageData);
  const sharpness = computeSharpness(luma, imageData.width, imageData.height);
  const brightness = computeBrightness(luma);
  const glareDetected = detectGlare(luma);
  const framed = detectFraming(luma, imageData.width, imageData.height);

  const overallOk =
    sharpness >= SHARPNESS_MIN &&
    brightness >= BRIGHTNESS_MIN &&
    brightness <= BRIGHTNESS_MAX &&
    !glareDetected &&
    framed;

  return { sharpness, brightness, glareDetected, framed, overallOk };
}

function toLuma(imageData: ImageData): Float32Array {
  const { data, width, height } = imageData;
  const luma = new Float32Array(width * height);
  for (let i = 0, p = 0; i < data.length; i += 4, p++) {
    luma[p] = 0.299 * data[i] + 0.587 * data[i + 1] + 0.114 * data[i + 2];
  }
  return luma;
}

function computeBrightness(luma: Float32Array): number {
  let sum = 0;
  for (let i = 0; i < luma.length; i++) sum += luma[i];
  return sum / luma.length;
}

function detectGlare(luma: Float32Array): boolean {
  let overexposed = 0;
  for (let i = 0; i < luma.length; i++) {
    if (luma[i] >= GLARE_LUMA_THRESHOLD) overexposed++;
  }
  return overexposed / luma.length > GLARE_RATIO_MAX;
}

function computeSharpness(luma: Float32Array, width: number, height: number): number {
  const laplacian: number[] = [];
  for (let y = 1; y < height - 1; y++) {
    for (let x = 1; x < width - 1; x++) {
      const idx = y * width + x;
      const value =
        4 * luma[idx] -
        luma[idx - 1] -
        luma[idx + 1] -
        luma[idx - width] -
        luma[idx + width];
      laplacian.push(value);
    }
  }
  if (laplacian.length === 0) return 0;

  const mean = laplacian.reduce((a, b) => a + b, 0) / laplacian.length;
  const variance = laplacian.reduce((a, b) => a + (b - mean) ** 2, 0) / laplacian.length;
  return variance;
}

function detectFraming(luma: Float32Array, width: number, height: number): boolean {
  const marginX = Math.round(width * FRAME_MARGIN_RATIO_X);
  const marginY = Math.round(height * FRAME_MARGIN_RATIO_Y);

  const topEdgeContrast = rowContrast(luma, width, marginY);
  const bottomEdgeContrast = rowContrast(luma, width, height - marginY - 1);
  const leftEdgeContrast = columnContrast(luma, width, height, marginX);
  const rightEdgeContrast = columnContrast(luma, width, height, width - marginX - 1);

  const edges = [topEdgeContrast, bottomEdgeContrast, leftEdgeContrast, rightEdgeContrast];
  return edges.filter(contrast => contrast >= FRAME_CONTRAST_MIN).length >= FRAME_EDGES_REQUIRED;
}

/**
 * Calcule le rectangle source à extraire de l'image d'origine pour reproduire un
 * recadrage `object-fit: cover` vers un ratio cible (ex. la vidéo affichée dans un
 * viewport 4:3). Nécessaire pour que l'image analysée corresponde pixel-pour-pixel
 * (en proportions) à ce que l'utilisateur voit réellement dans le cadre de capture.
 */
export function computeCoverSourceRect(
  sourceWidth: number,
  sourceHeight: number,
  targetWidth: number,
  targetHeight: number
): { sx: number; sy: number; sw: number; sh: number } {
  const sourceRatio = sourceWidth / sourceHeight;
  const targetRatio = targetWidth / targetHeight;

  if (sourceRatio > targetRatio) {
    const sw = sourceHeight * targetRatio;
    return { sx: (sourceWidth - sw) / 2, sy: 0, sw, sh: sourceHeight };
  }

  const sh = sourceWidth / targetRatio;
  return { sx: 0, sy: (sourceHeight - sh) / 2, sw: sourceWidth, sh };
}

function rowContrast(luma: Float32Array, width: number, y: number): number {
  let min = Infinity;
  let max = -Infinity;
  for (let x = 0; x < width; x++) {
    const value = luma[y * width + x];
    if (value < min) min = value;
    if (value > max) max = value;
  }
  return max - min;
}

function columnContrast(luma: Float32Array, width: number, height: number, x: number): number {
  let min = Infinity;
  let max = -Infinity;
  for (let y = 0; y < height; y++) {
    const value = luma[y * width + x];
    if (value < min) min = value;
    if (value > max) max = value;
  }
  return max - min;
}
