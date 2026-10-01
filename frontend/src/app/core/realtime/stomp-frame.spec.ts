import { encodeFrame, StompParser } from './stomp-frame';

describe('encodeFrame', () => {
  it('writes the command, headers, a blank line and the NULL terminator', () => {
    expect(encodeFrame({ command: 'CONNECT', headers: { 'accept-version': '1.2' } })).toBe(
      'CONNECT\naccept-version:1.2\n\n\0',
    );
  });

  it('includes a body', () => {
    expect(encodeFrame({ command: 'SEND', headers: { destination: '/x' }, body: 'hi' })).toBe(
      'SEND\ndestination:/x\n\nhi\0',
    );
  });

  it('escapes header values on frames other than CONNECT', () => {
    expect(encodeFrame({ command: 'SUBSCRIBE', headers: { id: 'a:b\\c\nd' } })).toBe(
      'SUBSCRIBE\nid:a\\cb\\\\c\\nd\n\n\0',
    );
  });

  it('leaves CONNECT header values untouched, as the protocol requires', () => {
    expect(encodeFrame({ command: 'CONNECT', headers: { Authorization: 'Bearer a:b' } })).toBe(
      'CONNECT\nAuthorization:Bearer a:b\n\n\0',
    );
  });
});

describe('StompParser', () => {
  it('parses a MESSAGE frame', () => {
    const frames = new StompParser().push(
      'MESSAGE\ndestination:/topic/catalog\nsubscription:sub-0\n\n{"type":"PRODUCT_CHANGED"}\0',
    );

    expect(frames).toEqual([
      {
        command: 'MESSAGE',
        headers: { destination: '/topic/catalog', subscription: 'sub-0' },
        body: '{"type":"PRODUCT_CHANGED"}',
      },
    ]);
  });

  it('skips heart-beat newlines between frames', () => {
    const frames = new StompParser().push('\n\nCONNECTED\nversion:1.2\n\n\0\n');

    expect(frames.map((frame) => frame.command)).toEqual(['CONNECTED']);
  });

  it('parses several frames delivered together', () => {
    const frames = new StompParser().push('MESSAGE\nid:1\n\na\0MESSAGE\nid:2\n\nb\0');

    expect(frames.map((frame) => frame.body)).toEqual(['a', 'b']);
  });

  it('waits for the rest of a frame split across chunks', () => {
    const parser = new StompParser();

    expect(parser.push('MESSAGE\nid:1\n\nhel')).toEqual([]);
    expect(parser.push('lo\0')).toEqual([{ command: 'MESSAGE', headers: { id: '1' }, body: 'hello' }]);
  });

  it('unescapes header values', () => {
    const [frame] = new StompParser().push('MESSAGE\nk:a\\cb\\\\c\\nd\\re\n\n\0');

    expect(frame.headers['k']).toBe('a:b\\c\nd\re');
  });

  it('keeps the first occurrence of a repeated header', () => {
    const [frame] = new StompParser().push('MESSAGE\nk:first\nk:second\n\n\0');

    expect(frame.headers['k']).toBe('first');
  });

  it('does not unescape CONNECTED headers', () => {
    const [frame] = new StompParser().push('CONNECTED\nserver:a\\cb\n\n\0');

    expect(frame.headers['server']).toBe('a\\cb');
  });

  it('tolerates CRLF line endings', () => {
    const [frame] = new StompParser().push('MESSAGE\r\nid:1\r\n\r\nbody\0');

    expect(frame).toEqual({ command: 'MESSAGE', headers: { id: '1' }, body: 'body' });
  });
});
