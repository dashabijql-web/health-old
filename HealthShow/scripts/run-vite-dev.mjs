import path from 'node:path'
import { fileURLToPath, pathToFileURL } from 'node:url'

const viteCli = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..', 'node_modules', 'vite', 'bin', 'vite.js')
await import(pathToFileURL(viteCli).href)
