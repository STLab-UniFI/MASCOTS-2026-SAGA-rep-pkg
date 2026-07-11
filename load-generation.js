import http from 'k6/http';
import { check, sleep } from 'k6';

const targetIterations = __ENV.REQ_COUNT ? parseInt(__ENV.REQ_COUNT) : 5750;

export const options = {
  vus: 1,          
  iterations: targetIterations,  
  duration: '2h', // Changed from maxDuration to duration
};

export default function () {
  const url = 'http://localhost:8080/orchestrate/default-topology/';
  
  // If your POST request requires a body, define it here
  const payload = JSON.stringify({});

  // Define headers (adjust Content-Type if needed)
  const params = {
    headers: {
      'Content-Type': 'application/json',
    },
  };

  // 1. Make the POST request
  const res = http.post(url, payload, params);

  // 2. Validate the response (optional but recommended)
  check(res, {
    'is status 200': (r) => r.status === 200,
  });
}