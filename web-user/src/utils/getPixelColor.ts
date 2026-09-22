export function getPixelColor(
  context:CanvasRenderingContext2D, x:number, y:number
) {
  // 坐标越界会让 getImageData 抛 IndexSizeError：歌词上下渐隐遮罩滚出可视区时，
  // 取到的 y 会超出画布高度，这里先夹紧到画布范围内
  const safeX = Math.min(Math.max(0, Math.round(x)), Math.max(0, context.canvas.width - 1));
  const safeY = Math.min(Math.max(0, Math.round(y)), Math.max(0, context.canvas.height - 1));
  // x轴坐标 ， y轴坐标， 1，1取色的范围像素值
  const imageData = context.getImageData(
    safeX, safeY, 1, 1
  ); 
  const pixel = imageData.data;
  const r = pixel[0];
  const g = pixel[1];
  const b = pixel[2];
  let rHex = r.toString(16);
  r < 16 && (rHex = '0' + rHex);
  let gHex = g.toString(16);
  g < 16 && (gHex = '0' + gHex);
  let bHex = b.toString(16);
  b < 16 && (bHex = '0' + bHex);
  const rgbColor = 'rgb(' + r + ',' + g + ',' + b + ')';
  const hexColor = '#' + rHex + gHex + bHex;
  return {
    rgb: rgbColor,
    hex: hexColor
  };
}