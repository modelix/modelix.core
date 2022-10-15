export async function greeting(name: string = 'World') {
  return greetingSync(name);
}

export function greetingSync(name: string = 'World') {
  return `Hello, ${name}!`;
}

export default greeting;
