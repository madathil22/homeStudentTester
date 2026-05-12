import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';
import { parseAnswerBank, parseQuestionBank } from '../src/markdownParser.js';
import { scoreSubmission } from '../src/scorer.js';

test('parses sample question and answer banks', async () => {
  const questionMarkdown = await readFile('public/sample-question-bank.md', 'utf8');
  const answerMarkdown = await readFile('public/sample-answer-bank.md', 'utf8');

  const questionBank = parseQuestionBank(questionMarkdown);
  const answerBank = parseAnswerBank(answerMarkdown);

  assert.equal(questionBank.title, 'Grade 4 ELA Practice Test');
  assert.equal(questionBank.passages.length, 1);
  assert.equal(questionBank.questions.length, 3);
  assert.equal(questionBank.questions[0].options[1].label, 'B');
  assert.equal(answerBank['1'].answer, 'B');
  assert.match(answerBank['2'].rubric, /Clearly explains/);
});

test('scores objective questions and leaves written responses for review', () => {
  const activeTest = {
    questions: [
      { number: '1', type: 'multiple_choice', points: 1 },
      { number: '2', type: 'short_response', points: 2 }
    ]
  };
  const answerBank = {
    1: { answer: 'B', points: 1 },
    2: { points: 2, rubric: 'Review manually.' }
  };
  const submission = {
    answers: {
      1: 'B',
      2: 'Because she is curious.'
    }
  };

  const score = scoreSubmission(activeTest, answerBank, submission);

  assert.equal(score.earned, 1);
  assert.equal(score.possible, 3);
  assert.equal(score.byQuestion['1'].status, 'Correct');
  assert.equal(score.byQuestion['2'].status, 'Needs review');
});

test('question parser stops before an appended answer bank', () => {
  const combinedMarkdown = `# Grade 3 Math NJSLA Practice Test

## Question 1

Type: multiple_choice
Points: 1

Lena has 6 bags. Each bag has 4 apples. How many apples does Lena have in all?

A. 10
B. 20
C. 24
D. 30

---

# Answer Bank

## Question 1

Answer: C
Points: 1

Explanation: 6 x 4 = 24 apples.
`;

  const questionBank = parseQuestionBank(combinedMarkdown);
  const answerBank = parseAnswerBank(combinedMarkdown);

  assert.equal(questionBank.questions.length, 1);
  assert.equal(questionBank.questions[0].type, 'multiple_choice');
  assert.equal(questionBank.questions[0].prompt.includes('---'), false);
  assert.equal(answerBank['1'].answer, 'C');
});
