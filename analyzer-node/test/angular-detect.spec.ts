import { readFile } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { Ajv2020 } from 'ajv/dist/2020.js';
import { describe, expect, it } from 'vitest';
import { cleanVersion, detectAngular, SCORE_WITH_CORE, SCORE_WORKSPACE_ONLY } from '../src/adapters/angular/detect.js';

const contracts = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..', '..', 'contracts');
const ANGULAR_JSON = JSON.stringify({ version: 1, projects: { 'shop-web': { projectType: 'application' } } });

describe('nhận diện Angular', () => {
  it('angular.json + @angular/core → điểm cao, có phiên bản', () => {
    const r = detectAngular({
      'angular.json': ANGULAR_JSON,
      'package.json': JSON.stringify({ dependencies: { '@angular/core': '^21.0.3' } })
    });
    expect(r).toEqual({
      score: SCORE_WITH_CORE,
      stackName: 'Angular',
      version: '21.0.3',
      reason: 'angular.json + @angular/core ^21.0.3'
    });
  });

  it('chỉ có angular.json → điểm thấp hơn, không phiên bản', () => {
    const r = detectAngular({ 'angular.json': ANGULAR_JSON, 'package.json': '{ hỏng' });
    expect(r.score).toBe(SCORE_WORKSPACE_ONLY);
    expect(r.version).toBeNull();
  });

  it('angular.json không có projects hoặc sai cú pháp → không phải Angular', () => {
    expect(detectAngular({ 'angular.json': '{}' }).score).toBe(0);
    expect(detectAngular({ 'angular.json': 'không phải json' }).score).toBe(0);
    expect(detectAngular({}).score).toBe(0);
  });

  it('làm sạch dải phiên bản', () => {
    expect(cleanVersion('~15.2')).toBe('15.2');
    expect(cleanVersion('>=17.0.0 <18')).toBe('17.0.0');
    expect(cleanVersion('latest')).toBeNull();
  });

  it('kết quả khớp contracts/detect-result.schema.json', async () => {
    const schema = JSON.parse(await readFile(path.join(contracts, 'detect-result.schema.json'), 'utf8'));
    const validate = new Ajv2020({ allErrors: true }).compile(schema);
    const samples: Record<string, string>[] = [{ 'angular.json': ANGULAR_JSON }, {}];
    for (const files of samples) {
      expect(validate(detectAngular(files)), JSON.stringify(validate.errors)).toBe(true);
    }
  });
});
