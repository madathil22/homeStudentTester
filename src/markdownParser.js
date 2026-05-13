const QUESTION_HEADING = /^##\s+Question\s+(\d+)\s*$/i;
const PASSAGE_HEADING = /^##\s+Passage:\s*(.+)$/i;
const OPTION_LINE = /^([A-Z])\.\s+(.+)$/;
const META_LINE = /^([A-Za-z][A-Za-z ]*):\s*(.*)$/;

const supportedTypes = new Set([
  'multiple_choice',
  'multi_select',
  'short_response',
  'essay',
  'text'
]);

export function parseQuestionBank(markdown) {
  const lines = normalize(markdown);
  const titleLine = lines.find((line) => /^#\s+/.test(line));
  const title = titleLine?.replace(/^#\s+/, '').trim();
  const instructions = [];
  const passages = [];
  const questions = [];
  let currentPassage = null;
  let currentQuestion = null;

  if (!title) {
    throw new Error('Question bank must start with a # test title.');
  }

  for (let index = 0; index < lines.length; index += 1) {
    const line = lines[index];
    const questionMatch = line.match(QUESTION_HEADING);
    const passageMatch = line.match(PASSAGE_HEADING);

    if (/^#\s+Answer Bank\s*$/i.test(line)) break;
    if (isDivider(line)) continue;
    if (/^#\s+/.test(line)) continue;

    if (passageMatch) {
      currentQuestion = null;
      currentPassage = {
        id: String(passages.length + 1),
        title: passageMatch[1].trim(),
        body: ''
      };
      passages.push(currentPassage);
      continue;
    }

    if (questionMatch) {
      currentPassage = null;
      currentQuestion = {
        number: questionMatch[1],
        type: '',
        points: 1,
        prompt: '',
        options: [],
        passageIds: passages.length ? [passages.at(-1).id] : []
      };
      questions.push(currentQuestion);
      continue;
    }

    if (currentQuestion) {
      const metaMatch = line.match(META_LINE);
      const optionMatch = line.match(OPTION_LINE);

      if (metaMatch && ['type', 'points'].includes(metaMatch[1].toLowerCase())) {
        const key = metaMatch[1].toLowerCase();
        if (key === 'type') currentQuestion.type = metaMatch[2].trim();
        if (key === 'points') currentQuestion.points = parsePoints(metaMatch[2], currentQuestion.number);
        continue;
      }

      if (optionMatch) {
        currentQuestion.options.push({
          label: optionMatch[1],
          text: optionMatch[2].trim()
        });
        continue;
      }

      currentQuestion.prompt = appendBlock(currentQuestion.prompt, line);
      continue;
    }

    if (currentPassage) {
      currentPassage.body = appendBlock(currentPassage.body, line);
      continue;
    }

    instructions.push(line);
  }

  const cleanedQuestions = questions.map((question) => ({
    ...question,
    prompt: question.prompt.trim(),
    type: question.type.trim()
  }));

  validateQuestionBank(cleanedQuestions);

  return {
    title,
    instructions: instructions.join('\n').trim(),
    passages: passages.map((passage) => ({ ...passage, body: passage.body.trim() })),
    questions: cleanedQuestions
  };
}

export function parseAnswerBank(markdown) {
  const lines = normalize(markdown);
  const answers = {};
  let current = null;
  let collecting = null;
  let inAnswerBank = !lines.some((line) => /^#\s+Answer Bank\s*$/i.test(line));

  for (const line of lines) {
    if (/^#\s+Answer Bank\s*$/i.test(line)) {
      inAnswerBank = true;
      current = null;
      collecting = null;
      continue;
    }

    if (!inAnswerBank || isDivider(line)) continue;

    const questionMatch = line.match(QUESTION_HEADING);

    if (questionMatch) {
      current = {
        number: questionMatch[1],
        answer: '',
        accepted: [],
        points: null,
        explanation: '',
        rubric: '',
        sampleAnswer: ''
      };
      answers[current.number] = current;
      collecting = null;
      continue;
    }

    if (!current || /^#\s+/.test(line)) continue;

    const metaMatch = line.match(META_LINE);
    if (metaMatch) {
      const key = metaMatch[1].toLowerCase();
      const value = metaMatch[2].trim();

      if (key === 'answer') {
        current.answer = value;
        collecting = null;
        continue;
      }
      if (key === 'accept') {
        current.accepted = splitAnswers(value);
        collecting = null;
        continue;
      }
      if (key === 'points') {
        current.points = parsePoints(value, current.number);
        collecting = null;
        continue;
      }
      if (key === 'explanation') {
        current.explanation = value;
        collecting = 'explanation';
        continue;
      }
      if (key === 'rubric') {
        current.rubric = value;
        collecting = 'rubric';
        continue;
      }
      if (key === 'sample answer') {
        current.sampleAnswer = value;
        collecting = 'sampleAnswer';
        continue;
      }
    }

    if (collecting) {
      current[collecting] = appendBlock(current[collecting], line);
    }
  }

  return answers;
}

export function splitAnswers(value) {
  return value
    .split(',')
    .map((item) => item.trim())
    .filter(Boolean);
}

function normalize(markdown) {
  return String(markdown ?? '').replace(/\r\n/g, '\n').split('\n');
}

function appendBlock(existing, line) {
  if (!existing && !line.trim()) return '';
  return `${existing}${existing ? '\n' : ''}${line}`.trimEnd();
}

function isDivider(line) {
  return /^-{3,}\s*$/.test(line.trim());
}

function parsePoints(value, questionNumber) {
  const points = Number(value);
  if (!Number.isFinite(points) || points < 0) {
    throw new Error(`Question ${questionNumber} has invalid Points value.`);
  }
  return points;
}

function validateQuestionBank(questions) {
  if (!questions.length) {
    throw new Error('Question bank must include at least one ## Question N section.');
  }

  for (const question of questions) {
    if (!question.type) throw new Error(`Question ${question.number} is missing Type:.`);
    if (!supportedTypes.has(question.type)) {
      throw new Error(`Question ${question.number} has unsupported Type: ${question.type}.`);
    }
    if (!question.prompt) throw new Error(`Question ${question.number} is missing a prompt.`);
    if (question.type === 'multiple_choice' && question.options.length < 2) {
      throw new Error(`Question ${question.number} needs at least two answer options.`);
    }
    if (question.type === 'multi_select' && question.options.length < 2) {
      throw new Error(`Question ${question.number} needs at least two answer options.`);
    }
  }
}
