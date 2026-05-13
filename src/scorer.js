import { splitAnswers } from './markdownParser.js';

const objectiveTypes = new Set(['multiple_choice', 'multi_select', 'text']);

export function scoreSubmission(test, answerBank, submission) {
  const byQuestion = {};
  let earned = 0;
  let possible = 0;

  for (const question of test?.questions ?? []) {
    const key = question.number;
    const answerInfo = answerBank?.[key];
    const points = answerInfo?.points ?? question.points ?? 0;
    const studentAnswer = submission.answers?.[key] ?? '';
    const result = {
      questionNumber: key,
      type: question.type,
      points,
      earned: null,
      status: 'Needs review',
      expected: answerInfo?.answer ?? '',
      explanation: answerInfo?.explanation ?? '',
      rubric: answerInfo?.rubric ?? '',
      sampleAnswer: answerInfo?.sampleAnswer ?? ''
    };

    possible += points;

    if (objectiveTypes.has(question.type)) {
      const isCorrect = isObjectiveCorrect(question.type, studentAnswer, answerInfo);
      result.earned = isCorrect ? points : 0;
      result.status = isCorrect ? 'Correct' : 'Incorrect';
      earned += result.earned;
    }

    byQuestion[key] = result;
  }

  return {
    earned,
    possible,
    byQuestion,
    scoredAt: new Date().toISOString()
  };
}

function isObjectiveCorrect(type, studentAnswer, answerInfo) {
  const expected = answerInfo?.answer ?? '';
  const accepted = answerInfo?.accepted ?? [];

  if (type === 'multi_select') {
    return normalizeSet(studentAnswer).join('|') === normalizeSet(expected).join('|');
  }

  const candidates = [expected, ...accepted].map(normalizeText);
  return candidates.includes(normalizeText(studentAnswer));
}

function normalizeSet(value) {
  return splitAnswers(String(value))
    .map((item) => item.toUpperCase())
    .sort();
}

function normalizeText(value) {
  return String(value).trim().toLowerCase();
}
