export interface StompFrame {
  readonly command: string;
  readonly headers: Readonly<Record<string, string>>;
  readonly body?: string;
}

// STOMP 1.2 leaves CONNECT and CONNECTED headers unescaped.
const UNESCAPED_COMMANDS = new Set(['CONNECT', 'CONNECTED']);

function escapeHeader(value: string): string {
  return value
    .replace(/\\/g, '\\\\')
    .replace(/\r/g, '\\r')
    .replace(/\n/g, '\\n')
    .replace(/:/g, '\\c');
}

function unescapeHeader(value: string): string {
  return value.replace(/\\(.)/g, (_match, char: string) => {
    switch (char) {
      case 'n':
        return '\n';
      case 'r':
        return '\r';
      case 'c':
        return ':';
      default:
        return char;
    }
  });
}

export function encodeFrame({ command, headers, body = '' }: StompFrame): string {
  const escape = UNESCAPED_COMMANDS.has(command) ? (value: string) => value : escapeHeader;
  const lines = Object.entries(headers).map(([name, value]) => `${escape(name)}:${escape(value)}`);
  return `${command}\n${lines.map((line) => `${line}\n`).join('')}\n${body}\0`;
}

function decodeFrame(raw: string): StompFrame | null {
  const text = raw.replace(/^[\r\n]+/, '');
  if (text === '') {
    return null;
  }
  const separator = text.search(/\r?\n\r?\n/);
  const head = separator === -1 ? text : text.slice(0, separator);
  const body = separator === -1 ? '' : text.slice(separator).replace(/^\r?\n\r?\n/, '');
  const [command, ...headerLines] = head.split(/\r?\n/);
  const unescape = UNESCAPED_COMMANDS.has(command) ? (value: string) => value : unescapeHeader;
  const headers: Record<string, string> = {};
  for (const line of headerLines) {
    const colon = line.indexOf(':');
    if (colon === -1) {
      continue;
    }
    const name = unescape(line.slice(0, colon));
    // A repeated header keeps its first value.
    headers[name] ??= unescape(line.slice(colon + 1));
  }
  return { command, headers, body };
}

// Frames end with NULL, may arrive several per chunk or split across chunks, and are separated by heart-beat newlines.
export class StompParser {
  private pending = '';

  push(chunk: string): StompFrame[] {
    this.pending += chunk;
    const frames: StompFrame[] = [];
    let end = this.pending.indexOf('\0');
    while (end !== -1) {
      const frame = decodeFrame(this.pending.slice(0, end));
      if (frame) {
        frames.push(frame);
      }
      this.pending = this.pending.slice(end + 1);
      end = this.pending.indexOf('\0');
    }
    return frames;
  }
}
