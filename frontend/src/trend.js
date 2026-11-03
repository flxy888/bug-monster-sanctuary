// Saved scores only. Unknown/incompatible scoring configurations break the line.
export function trendPoints(history) {
  return history.slice(0,50).reverse().map((s,i,all)=>({ ...s,x:20+i*(660/Math.max(1,all.length-1)),
    y:s.status==='SUCCESS' && Number.isInteger(s.score)?170-s.score*1.4:null }))
}
export function trendSegments(points) {
  return points.slice(1).flatMap((p,i)=>{const prev=points[i];return p.y!==null && prev.y!==null && p.scoringConfigId && p.scoringConfigId===prev.scoringConfigId ? [{a:prev,b:p}] : []})
}
