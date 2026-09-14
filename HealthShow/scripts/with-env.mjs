import { spawn } from 'node:child_process'
import process from 'node:process'

const args = process.argv.slice(2)
const separator = args.indexOf('--')
if (separator === -1 || separator === args.length - 1) {
  console.error('usage: node scripts/with-env.mjs KEY=value [KEY=value...] -- command [args...]')
  process.exit(2)
}

const envPairs = args.slice(0, separator)
const command = args[separator + 1]
const commandArgs = args.slice(separator + 2)
const extraEnv = {}

for (const pair of envPairs) {
  const index = pair.indexOf('=')
  if (index <= 0) {
    console.error(`invalid env assignment: ${pair}`)
    process.exit(2)
  }
  extraEnv[pair.slice(0, index)] = pair.slice(index + 1)
}

const child = spawn(command, commandArgs, {
  stdio: 'inherit',
  shell: process.platform === 'win32',
  env: {
    ...process.env,
    ...extraEnv
  }
})

child.on('close', (code) => process.exit(code ?? 1))
