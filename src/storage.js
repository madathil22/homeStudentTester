import { mkdir, readFile, writeFile } from 'node:fs/promises';
import path from 'node:path';

const DATA_DIR = path.join(process.cwd(), 'data');
const DATA_FILE = path.join(DATA_DIR, 'app.json');

const emptyState = {
  activeTest: null,
  answerBank: null,
  submissions: []
};

async function ensureDataFile() {
  await mkdir(DATA_DIR, { recursive: true });

  try {
    await readFile(DATA_FILE, 'utf8');
  } catch (error) {
    if (error.code !== 'ENOENT') throw error;
    await writeState(emptyState);
  }
}

export async function readState() {
  await ensureDataFile();
  const raw = await readFile(DATA_FILE, 'utf8');

  try {
    return { ...emptyState, ...JSON.parse(raw) };
  } catch {
    throw new Error('data/app.json contains invalid JSON.');
  }
}

export async function writeState(state) {
  await mkdir(DATA_DIR, { recursive: true });
  await writeFile(DATA_FILE, `${JSON.stringify(state, null, 2)}\n`);
}

export async function updateState(mutator) {
  const state = await readState();
  const nextState = await mutator(state);
  await writeState(nextState ?? state);
  return nextState ?? state;
}
