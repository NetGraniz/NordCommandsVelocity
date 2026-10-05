'use strict';
const assert=require('node:assert/strict');
const fs=require('node:fs'),path=require('node:path'),net=require('node:net');
const {spawn}=require('node:child_process');
const mineflayer=require('mineflayer');
const root='C:\\Users\\artyo\\Documents\\Codex\\nordcommands-velocity-test-20261004';
const previous='C:\\Users\\artyo\\Documents\\Codex\\nordcommands-paper-test-20261004';
const java='C:\\Program Files\\Java\\jdk-25\\bin\\java.exe';
const baseline=process.argv.includes('--baseline');
const source=path.resolve(__dirname,'..');
const results=[],children=[],clients=new Set();
const sleep=ms=>new Promise(resolve=>setTimeout(resolve,ms));
let paper,proxy,seq=0;
async function until(test,label,timeout=15000){const start=Date.now();while(!await test()){if(Date.now()-start>timeout)throw Error('Timeout: '+label);await sleep(50);}}
function pass(name){results.push(name);console.log('PASS: '+name);}
async function closed(port){await new Promise((resolve,reject)=>{const socket=net.connect({host:'127.0.0.1',port});socket.setTimeout(1500);socket.once('connect',()=>{socket.destroy();reject(Error('Fixture port in use: '+port));});socket.once('error',e=>{socket.destroy();if(e.code==='ECONNREFUSED')resolve();else reject(e);});socket.once('timeout',()=>{socket.destroy();reject(Error('Cannot verify fixture port '+port));});});}
function fixture(){
  assert(root.startsWith('C:\\Users\\artyo\\Documents\\Codex\\'));
  for(const sub of ['paper','proxy/plugins','paper/config','paper/plugins/NordCommands','paper/plugins/NordChat','paper/plugins/NordFilter'])fs.mkdirSync(path.join(root,sub),{recursive:true});
  // Test outputs may be rewritten only in this exact isolated fixture.
  for(const version of ['1.0.0','1.1.0']){const existing=path.join(root,'proxy/plugins/NordCommands-Velocity-'+version+'.jar');if(fs.existsSync(existing))fs.renameSync(existing,path.join(root,'retired-velocity-'+version+'-'+Date.now()+'.jar'));}
  for(const sub of ['paper/server.jar','proxy/velocity.jar'])fs.copyFileSync(path.join(previous,sub),path.join(root,sub));
  for(const label of ['NordChat','NordFilter']){
    const version=label==='NordChat'?'0.1.3':'1.1.0';
    fs.copyFileSync('Z:\\Minecraft Plagins\\'+label+'\\releases\\'+version+'\\'+label+'-'+version+'.jar',path.join(root,'paper/plugins/'+label+'-'+version+'.jar'));
  }
  fs.copyFileSync('Z:\\Minecraft Plagins\\NordCommandsPaper\\releases\\1.1.0\\NordCommands-Paper-1.1.0.jar',path.join(root,'paper/plugins/NordCommands-Paper-1.1.0.jar'));
  fs.copyFileSync('Z:\\Minecraft Plagins\\NordCommandsPaper\\test-support\\build\\CommandsTestProbe.jar',path.join(root,'paper/plugins/CommandsTestProbe.jar'));
  fs.copyFileSync(baseline?'Z:\\Minecraft Proxy\\plugins\\NordCommands-Velocity-1.0.0.jar':path.join(source,'build/NordCommands-Velocity-1.1.0.jar'),path.join(root,'proxy/plugins/NordCommands-Velocity-'+(baseline?'1.0.0':'1.1.0')+'.jar'));
  fs.copyFileSync(path.join(__dirname,'build/VelocityCommandsTestProbe.jar'),path.join(root,'proxy/plugins/VelocityCommandsTestProbe.jar'));
  fs.writeFileSync(path.join(root,'paper/eula.txt'),'eula=true\n');
  fs.writeFileSync(path.join(root,'paper/server.properties'),[
    'server-ip=127.0.0.1','server-port=25696','online-mode=false','enforce-secure-profile=false',
    'max-players=20','view-distance=2','simulation-distance=2','enable-rcon=false','enable-query=false',
    'level-name=nordcommands-synthetic-world','level-type=minecraft:flat','generate-structures=false',
    'spawn-protection=0','pause-when-empty-seconds=-1','gamemode=creative','force-gamemode=true','difficulty=peaceful',''].join('\n'));
  const secret=require('node:crypto').randomBytes(32).toString('hex');
  const global=fs.readFileSync(path.join(previous,'paper/config/paper-global.yml'),'utf8');
  const section=/  velocity:\r?\n    enabled: (?:true|false)\r?\n    online-mode: (?:true|false)\r?\n    secret: [^\r\n]*/;
  assert(section.test(global));
  fs.writeFileSync(path.join(root,'paper/config/paper-global.yml'),global.replace(section,'  velocity:\n    enabled: true\n    online-mode: false\n    secret: "'+secret+'"'));
  let velocity=fs.readFileSync(path.join(previous,'proxy/velocity.toml'),'utf8').replaceAll('25685','25695').replaceAll('25686','25696').replaceAll('25687','25697').replace('try = ["queue"]','try = ["main"]');
  assert(velocity.includes('bind = "127.0.0.1:25695"')&&velocity.includes('try = ["main"]'));
  fs.writeFileSync(path.join(root,'proxy/velocity.toml'),velocity);
  fs.writeFileSync(path.join(root,'proxy/local-test-forwarding.secret'),secret);
  fs.writeFileSync(path.join(root,'paper/plugins/NordCommands/config.yml'),'denied-message: No such command.\nallowed-commands: [safe, msg, login, register]\n');
  fs.writeFileSync(path.join(root,'paper/plugins/NordChat/config.yml'),'temporary-ignore-days: 7\nprivate-message-cooldown-millis: 500\nclickable-chat-names: true\n');
  fs.writeFileSync(path.join(root,'paper/plugins/NordChat/players.yml'),'{}\n');
  fs.copyFileSync('Z:\\Minecraft Plagins\\NordFilter\\src\\main\\resources\\config.yml',path.join(root,'paper/plugins/NordFilter/config.yml'));
  fs.writeFileSync(path.join(root,'paper/plugins/NordFilter/banwords.yml'),'words: [syntheticforbidden]\n');
  fs.writeFileSync(path.join(root,'paper/plugins/NordFilter/data.yml'),'{}\n');
}
function start(name,heap){
  const child=spawn(java,['-Xms64M','-Xmx'+heap,'-jar',name==='paper'?'server.jar':'velocity.jar',...(name==='paper'?['nogui']:[])],{cwd:path.join(root,name),windowsHide:true,stdio:['pipe','pipe','pipe']});
  const handle={name,child,output:'',exited:false};children.push(handle);
  for(const stream of [child.stdout,child.stderr])stream.on('data',b=>handle.output+=b.toString().replace(/\x1b\[[0-9;]*m/g,''));
  child.on('exit',()=>handle.exited=true);child.on('error',e=>{handle.output+=String(e);handle.exited=true;});return handle;
}
function connect(name){
  const bot=mineflayer.createBot({host:'127.0.0.1',port:25695,username:name,auth:'offline',version:'26.2',hideErrors:true,checkTimeoutInterval:30000});
  const c={bot,name,messages:[],roots:[],packets:0,ended:false,errors:[],tabs:[]};clients.add(c);
  bot.on('message',m=>c.messages.push(m.unsigned?m.unsigned.toString():m.toString()));
  bot._client.on('declare_commands',p=>{c.packets++;const r=p.nodes[p.rootIndex??p.root??0];c.roots=(r.children||[]).map(i=>p.nodes[i].extraNodeData?.name??p.nodes[i].name).filter(Boolean);});
  bot._client.on('tab_complete',p=>c.tabs.push(p));
  bot.on('end',()=>c.ended=true);bot.on('error',e=>c.errors.push(String(e)));bot.on('kicked',r=>c.kicked=JSON.stringify(r));return c;
}
async function joined(c){await until(()=>paper.output.includes(c.name+' joined the game')||c.ended,'join '+c.name,30000);assert(!c.ended,JSON.stringify({errors:c.errors,kicked:c.kicked}));await sleep(600);}
async function expect(c,regex,mark=0){await until(()=>c.messages.slice(mark).some(m=>regex.test(m))||c.ended,'message '+regex);assert(c.messages.slice(mark).some(m=>regex.test(m)),JSON.stringify({messages:c.messages,errors:c.errors,kicked:c.kicked}));}
async function cmd(c,text,regex){await sleep(1100);const mark=c.messages.length;c.bot.chat(text);if(regex)await expect(c,regex,mark);else await sleep(700);assert(!c.ended,c.kicked);}
async function control(text){const mark=proxy.output.length;proxy.child.stdin.write('vtcontrol '+text+'\n');await until(()=>proxy.output.slice(mark).includes('VTEST_OK '+text)||proxy.output.slice(mark).includes('VTEST_FAILED'),'control '+text,30000);assert(!proxy.output.slice(mark).includes('VTEST_FAILED'),proxy.output.slice(mark));}
async function state(){const id='s'+(++seq);await control('state '+id);const m=proxy.output.match(new RegExp('VSTATE '+id+' executions=(\\d+) guarded=(\\d+) notices=(-?\\d+) raw=([^\\r\\n]*)'));assert(m,proxy.output.slice(-1500));return {executions:+m[1],guarded:+m[2],notices:+m[3],raw:Buffer.from(m[4],'base64').toString()};}
async function api(c,text){const id='a'+(++seq);await control('api '+c.name+' '+id+' '+Buffer.from(text).toString('base64'));}
async function stop(){
  for(const c of clients)if(!c.ended)c.bot.quit();
  if(proxy&&!proxy.exited){proxy.child.stdin.write('shutdown\n');await until(()=>proxy.exited,'proxy stop',30000);}
  if(paper&&!paper.exited){paper.child.stdin.write('stop\n');await until(()=>paper.exited,'Paper stop',45000);}
  for(const h of children)fs.writeFileSync(path.join(root,h.name+'-runtime.log'),h.output);
}
async function run(){
  await closed(25695);await closed(25696);fixture();paper=start('paper','2G');
  await until(()=>paper.exited||/Done \(/.test(paper.output),'Paper startup',120000);assert(!paper.exited,paper.output.slice(-4000));
  proxy=start('proxy','512M');await until(()=>proxy.exited||/Done \(/.test(proxy.output),'proxy startup',45000);assert(!proxy.exited,proxy.output.slice(-4000));assert(proxy.output.includes('VPROBE_READY'));
  const a=connect('VTAlpha'),b=connect('VTBeta');await joined(a);await joined(b);
  await cmd(a,'/safe ordinary',/SAFE_EXECUTED ordinary/);await cmd(a,'/msg VTBeta ORIGINAL_PM',/ORIGINAL_PM/);await expect(b,/ORIGINAL_PM/);
  pass('Backend commands and PM pass through real MODERN proxy with all three current Paper plugins');
  assert.equal((await state()).executions,0);
  await cmd(a,'/vunsafe direct',/No such command/);assert.equal((await state()).executions,0);
  pass('Direct proxy command blocked before harmless executor');
  if(baseline){
    await cmd(a,'/safe proxyrewrite',/VUNSAFE_EXECUTED REWRITTEN_SYNTHETIC/);assert.equal((await state()).executions,1);
    await cmd(a,'/safe proxyalias',/VUNSAFE_EXECUTED ALIAS_SYNTHETIC/);assert.equal((await state()).executions,2);
    await cmd(a,'/safe proxynamespace',/VUNSAFE_EXECUTED NAMESPACE_SYNTHETIC/);assert.equal((await state()).executions,3);
    pass('OLD: normal-priority backend-to-proxy rewrite, alias and namespace reach harmless executor');
    await cmd(a,'/vunsafe resurrect',/VUNSAFE_EXECUTED resurrect/);assert.equal((await state()).executions,4);
    pass('OLD: normal-priority re-allow overrides first denial');return;
  }
  for(const text of ['/safe proxyrewrite','/safe proxyalias','/safe proxynamespace','/safe proxycase','/safe proxyforward','/vunsafe resurrect'])await cmd(a,text,/No such command/);
  assert.equal((await state()).executions,0);
  pass('Final gate blocks rewritten roots, aliases, namespace, case/space, forwarding and resurrected denial');
  await cmd(a,'/va direct',/No such command/);await cmd(a,'/test:vunsafe direct',/No such command/);
  await api(a,'   VuNsAfE api-leading-space');assert.equal((await state()).executions,0);
  pass('Direct aliases, namespaced roots and real API leading-space normalization are denied');
  await cmd(a,'/safe backendrewrite',/BACKEND_REWRITE/);await expect(b,/BACKEND_REWRITE/);
  await cmd(a,'/safe backendforward',/BACKEND_FORWARD/);await expect(b,/BACKEND_FORWARD/);
  pass('Backend-to-backend rewrite and explicit forwarding are preserved');
  await cmd(a,'/safe two  spaces ; /op Nobody',/SAFE_EXECUTED/);
  pass('Command-like argument payload remains harmless backend arguments');
  const before=paper.output.length;await cmd(a,'/safe proxydeny');assert(proxy.output.includes('VPROBE_BACKEND_DENIED'));assert(!paper.output.slice(before).includes('issued server command: /safe proxydeny'));
  pass('Existing denial of backend command is not changed to allow/forward');
  await until(()=>b.packets>0,'modern command tree');assert(b.roots.includes('safe')&&b.roots.includes('msg'),JSON.stringify(b.roots));
  for(const label of ['vunsafe','va','test:vunsafe','vlate','vguarded','velocity','server'])assert(!b.roots.includes(label),JSON.stringify(b.roots));
  pass('Real client command tree hides proxy roots including an intermediate handler addition');
  await control('permission VTAlpha nordcommands.bypass true');
  await cmd(a,'/vunsafe two  spaces ; /op Nobody',/VUNSAFE_EXECUTED/);
  let s=await state();assert.equal(s.executions,1);assert.equal(s.raw,'two  spaces ; /op Nobody');
  await control('permission VTAlpha nordcommands.bypass false');await cmd(a,'/vunsafe revoked',/No such command/);assert.equal((await state()).executions,1);
  pass('Bypass is currently permission-checked, preserves arguments and is immediately revocable');
  await control('permission VTAlpha nordcommands.bypass true');await cmd(a,'/vguarded',/permission|unknown|command/i);assert.equal((await state()).guarded,0);
  await control('permission VTAlpha vtest.guarded true');await cmd(a,'/vguarded',/VGUARDED_EXECUTED/);assert.equal((await state()).guarded,1);
  await control('permission VTAlpha vtest.guarded false');await cmd(a,'/vguarded',/permission|unknown|command/i);assert.equal((await state()).guarded,1);
  await control('permission VTAlpha nordcommands.bypass false');
  pass('Broad filter bypass does not grant the target command own permission');
  await control('register');await cmd(a,'/vdynamic',/No such command/);assert.equal((await state()).executions,1);await control('unregister');
  pass('Commands registered at runtime are denied without policy-cache refresh');
  for(const text of ['safe\nline','/vunsafe','vunsafe\targ','safe '+ 'x'.repeat(32763)])await api(a,text);
  assert.equal((await state()).executions,1);
  pass('Actual API invocations with malformed or oversized input fail closed');
  await control('burst VTAlpha');assert(proxy.output.includes('VBURST executions=0'));s=await state();assert(s.notices>=1&&s.notices<=2);
  pass('1000 isolated API denials stay blocked with bounded session state; not 1000 network players');
  a.bot.quit();await until(()=>a.ended,'disconnect Alpha');await sleep(500);assert.equal((await state()).notices,0);
  const again=connect('VTAlpha');await joined(again);await cmd(again,'/vunsafe reconnect',/No such command/);assert.equal((await state()).executions,1);
  pass('Disconnect cleans notice state and new session remains denied after reconnect');
  const consoleMark=proxy.output.length;proxy.child.stdin.write('vunsafe CONSOLE_SYNTHETIC\n');await until(()=>proxy.output.slice(consoleMark).includes('VUNSAFE_EXECUTED CONSOLE_SYNTHETIC'),'console command');assert.equal((await state()).executions,2);
  pass('Console remains outside player filtering');
  await control('offer VTBeta limit');
  console.log('LIMITATION: '+proxy.output.split('\n').find(line=>line.includes('VOFFER limit')));
  // Modern proxy suggestions have no public command-gate event. This diagnostic
  // is intentionally not a PASS claiming that hidden roots authorize suggestions.
  b.bot._client.write('tab_complete',{transactionId:901,text:'/vunsafe '});
  await until(()=>b.tabs.some(packet=>packet.transactionId===901),'modern completion response');
  const tab=b.tabs.find(packet=>packet.transactionId===901);
  console.log('LIMITATION: modern raw completion matches='+JSON.stringify(tab.matches));
  fs.writeFileSync(path.join(root,'suggestion-limitations.json'),JSON.stringify({api:proxy.output.split('\n').find(line=>line.includes('VOFFER limit')),modernPacket:tab,syntheticOnly:true},null,2)+'\n');
}
(async()=>{let failure;try{await run();}catch(e){failure=e;console.error(e.stack);}finally{try{await stop();}catch(e){failure=failure||e;console.error(e.stack);}fs.writeFileSync(path.join(root,'results.json'),JSON.stringify({passed:!failure,baseline,results,error:failure?String(failure):null,ordinaryClientSignedChatTested:false},null,2)+'\n');}if(failure)process.exitCode=1;})();
