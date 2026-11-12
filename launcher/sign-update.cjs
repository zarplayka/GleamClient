const {createHash, createPrivateKey, createPublicKey, sign} = require('node:crypto');
const {readFileSync, writeFileSync} = require('node:fs');
const path = require('node:path');

const [executable, output] = process.argv.slice(2);
if (!executable || !output || process.argv.length !== 4) {
  throw new Error('Usage: node launcher/sign-update.cjs <launcher.exe> <launcher-update.json>');
}

const config = JSON.parse(readFileSync(path.join(__dirname,'tauri.conf.json'),'utf8'));
const version = config.version;
if (!/^\d+\.\d+\.\d+$/.test(version) || version.split('.').some(part => String(Number(part)) !== part || Number(part) > 65535)) {
  throw new Error('Expected a stable Windows launcher version');
}

const keyPath = process.env.GLEAM_UPDATE_SIGNING_KEY ||
  path.join(process.env.LOCALAPPDATA,'GleamLauncherSigning','private-key.pem');
const key = createPrivateKey(readFileSync(keyPath));
const {x,y,crv} = createPublicKey(key).export({format:'jwk'});
if (crv !== 'P-256') throw new Error('Expected a P-256 update signing key');
const publicKey = Buffer.concat([Buffer.from([4]),Buffer.from(x,'base64url'),Buffer.from(y,'base64url')]);
if (publicKey.toString('base64') !== readFileSync(path.join(__dirname,'update-public-key.txt'),'utf8').trim()) {
  throw new Error('The signing key does not match the key embedded in the launcher');
}

const binary = readFileSync(executable);
const pe = binary.length >= 64 ? binary.readUInt32LE(0x3c) : 0;
if (!pe || pe + 6 > binary.length || binary.length > 128 * 1024 * 1024 ||
    binary.readUInt16LE(0) !== 0x5a4d || binary.readUInt32LE(pe) !== 0x4550 || binary.readUInt16LE(pe + 4) !== 0x8664) {
  throw new Error('Expected a Windows x64 executable up to 128 MiB');
}

const payload = Buffer.from(JSON.stringify({
  version, size:binary.length, sha256:createHash('sha256').update(binary).digest('hex'),
}));
const signature = sign('sha256',payload,key);
writeFileSync(output,JSON.stringify({
  payload:payload.toString('base64'), signature:signature.toString('base64'),
}) + '\n',{flag:'wx'});
console.log(`Signed launcher-v${version} update manifest`);
