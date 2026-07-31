import { analyzeImageQuality, computeCoverSourceRect } from './image-quality-analyzer';

const WIDTH = 40;
const HEIGHT = 30;

function buildImageData(pixel: (x: number, y: number) => number): ImageData {
  const data = new Uint8ClampedArray(WIDTH * HEIGHT * 4);
  for (let y = 0; y < HEIGHT; y++) {
    for (let x = 0; x < WIDTH; x++) {
      const value = pixel(x, y);
      const idx = (y * WIDTH + x) * 4;
      data[idx] = value;
      data[idx + 1] = value;
      data[idx + 2] = value;
      data[idx + 3] = 255;
    }
  }
  return { data, width: WIDTH, height: HEIGHT } as unknown as ImageData;
}

describe('analyzeImageQuality', () => {
  it('reports low sharpness and no framing for a uniformly grey image', () => {
    const imageData = buildImageData(() => 128);

    const report = analyzeImageQuality(imageData);

    expect(report.sharpness).toBeCloseTo(0, 5);
    expect(report.brightness).toBeCloseTo(128, 0);
    expect(report.glareDetected).toBe(false);
    expect(report.framed).toBe(false);
    expect(report.overallOk).toBe(false);
  });

  it('reports high sharpness for a checkerboard pattern', () => {
    const imageData = buildImageData((x, y) => ((x + y) % 2 === 0 ? 20 : 220));

    const report = analyzeImageQuality(imageData);

    expect(report.sharpness).toBeGreaterThan(1000);
    expect(report.framed).toBe(true);
  });

  it('detects glare when a large portion of the image is near-white', () => {
    const imageData = buildImageData((x, y) => (y < HEIGHT * 0.2 ? 250 : 128));

    const report = analyzeImageQuality(imageData);

    expect(report.glareDetected).toBe(true);
    expect(report.overallOk).toBe(false);
  });

  it('rejects images that are too dark or too bright', () => {
    const darkReport = analyzeImageQuality(buildImageData(() => 10));
    const brightReport = analyzeImageQuality(buildImageData(() => 230));

    expect(darkReport.overallOk).toBe(false);
    expect(brightReport.overallOk).toBe(false);
  });
});

describe('computeCoverSourceRect', () => {
  it('crops the sides when the source is wider than the target ratio', () => {
    const rect = computeCoverSourceRect(1920, 1080, 200, 150);

    expect(rect.sy).toBe(0);
    expect(rect.sh).toBe(1080);
    expect(rect.sw).toBeCloseTo(1440, 0);
    expect(rect.sx).toBeCloseTo(240, 0);
  });

  it('crops the top and bottom when the source is taller than the target ratio', () => {
    const rect = computeCoverSourceRect(480, 800, 200, 150);

    expect(rect.sx).toBe(0);
    expect(rect.sw).toBe(480);
    expect(rect.sh).toBeCloseTo(360, 0);
    expect(rect.sy).toBeCloseTo(220, 0);
  });

  it('returns the full frame when the source already matches the target ratio', () => {
    const rect = computeCoverSourceRect(800, 600, 200, 150);

    expect(rect).toEqual({ sx: 0, sy: 0, sw: 800, sh: 600 });
  });
});
