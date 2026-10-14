import ELK from 'elkjs/lib/elk.bundled.js';
const elk = new ELK();
self.onmessage = async (e: MessageEvent) => {
  try {
    const graph = await elk.layout({
      id: 'root',
      layoutOptions: {
        'elk.algorithm': 'layered',
        'elk.direction': 'RIGHT',
        'elk.spacing.nodeNode': '55',
        'elk.layered.spacing.nodeNodeBetweenLayers': '95',
      },
      children: e.data.nodes.map((n: { id: string }) => ({ id: n.id, width: 175, height: 90 })),
      edges: e.data.edges.map((x: { id: string; source: string; target: string }) => ({
        id: x.id,
        sources: [x.source],
        targets: [x.target],
      })),
    });
    self.postMessage({ nodes: graph.children });
  } catch {
    self.postMessage({ error: '自动布局失败，请缩小范围重试' });
  }
};
