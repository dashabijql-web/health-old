type XlsxModule = typeof import('xlsx')
type Html2Canvas = typeof import('html2canvas')['default']
type JsPdfConstructor = typeof import('jspdf')['jsPDF']
type MarkedParser = typeof import('marked')['marked']

let xlsxPromise: Promise<XlsxModule> | undefined
let html2canvasPromise: Promise<Html2Canvas> | undefined
let jsPdfPromise: Promise<JsPdfConstructor> | undefined
let markedPromise: Promise<MarkedParser> | undefined

export function getXLSX(): Promise<XlsxModule> {
  if (!xlsxPromise) {
    xlsxPromise = import('xlsx')
  }
  return xlsxPromise
}

export function getHtml2Canvas(): Promise<Html2Canvas> {
  if (!html2canvasPromise) {
    html2canvasPromise = import('html2canvas').then((mod) => mod.default)
  }
  return html2canvasPromise
}

export function getJsPDF(): Promise<JsPdfConstructor> {
  if (!jsPdfPromise) {
    jsPdfPromise = import('jspdf').then((mod) => mod.jsPDF)
  }
  return jsPdfPromise
}

export function getMarked(): Promise<MarkedParser> {
  if (!markedPromise) {
    markedPromise = import('marked').then(mod => {
      const parser = mod.marked
      parser.setOptions({ breaks: true, gfm: true })
      return parser
    })
  }
  return markedPromise
}

export async function renderMarkdown(markdown?: string | null): Promise<string> {
  if (!markdown) return ''
  const parser = await getMarked()
  return await parser.parse(markdown)
}
