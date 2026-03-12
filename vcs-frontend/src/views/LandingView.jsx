import { Button, Card, CardBody } from "@heroui/react";
import { Cloud, Server, Check, Zap, ShieldCheck } from 'lucide-react';

export default function LandingView() {
  return (
    <main className="flex-grow flex flex-col items-center pt-24 pb-16 px-6 text-center z-10">
      <h1 className="text-5xl md:text-7xl font-extrabold tracking-tight mb-6 max-w-4xl leading-tight text-white">
        Private, Fast, Reliable <br/>
        <span className="text-transparent bg-clip-text bg-gradient-to-r from-lime-400 to-emerald-300">
          DevOps Platform
        </span>
      </h1>
      <p className="text-zinc-400 max-w-2xl text-lg md:text-xl mb-16 leading-relaxed">
        Great self-hosted option that brings teams and developers high-efficiency, but easy operations from planning to production.
      </p>
      
      <div className="grid md:grid-cols-2 gap-8 max-w-5xl w-full mx-auto pb-24 text-left">
        
        <Card className="bg-zinc-900 border border-zinc-800 hover:border-lime-500/50 hover:shadow-[0_0_30px_rgba(163,230,53,0.1)] transition duration-300 group">
          <div className="h-48 bg-zinc-950 border-b border-zinc-800 relative flex items-center justify-center overflow-hidden">
            <div className="absolute inset-0 bg-[radial-gradient(circle_at_center,rgba(163,230,53,0.08)_0,transparent_60%)] opacity-0 group-hover:opacity-100 transition duration-500"></div>
            <Cloud className="w-32 h-32 text-zinc-800 group-hover:text-lime-500/20 transition duration-500" strokeWidth={1} />
            <div className="absolute text-lime-400 animate-float"><Zap size={48} strokeWidth={1.5} /></div>
          </div>
          <CardBody className="p-8 flex flex-col">
            <h3 className="text-3xl font-bold mb-6 text-center text-white">Root Cloud</h3>
            <ul className="space-y-4 mb-8 text-zinc-300 flex-grow">
              {["Choose your provider and region.", "For individual developers and enterprise users.", "The fastest way to have it!"].map((f, i) => (
                <li key={i} className="flex items-start gap-3">
                  <Check className="w-5 h-5 text-lime-400 flex-shrink-0 mt-0.5" strokeWidth={3} />
                  <span>{f}</span>
                </li>
              ))}
            </ul>
            <Button color="primary" variant="bordered" className="w-full py-6 text-base font-bold">
              Start Free Root Cloud Trial &rarr;
            </Button>
          </CardBody>
        </Card>

        <Card className="bg-zinc-900 border border-zinc-800 hover:border-lime-500/50 hover:shadow-[0_0_30px_rgba(163,230,53,0.1)] transition duration-300 group">
          <div className="h-48 bg-zinc-950 border-b border-zinc-800 relative flex items-center justify-center overflow-hidden">
            <div className="absolute inset-0 bg-[radial-gradient(circle_at_center,rgba(163,230,53,0.08)_0,transparent_60%)] opacity-0 group-hover:opacity-100 transition duration-500"></div>
            <Server className="w-32 h-32 text-zinc-800 group-hover:text-lime-500/20 transition duration-500" strokeWidth={1} />
            <div className="absolute text-lime-400 animate-float-reverse"><ShieldCheck size={48} strokeWidth={1.5} /></div>
          </div>
          <CardBody className="p-8 flex flex-col">
            <h3 className="text-3xl font-bold mb-6 text-center text-white">Root Enterprise</h3>
            <ul className="space-y-4 mb-8 text-zinc-300 flex-grow">
              {["Deploy an enhanced Root anywhere.", "Designed for enterprise users.", "Enjoy an enhanced experience!"].map((f, i) => (
                <li key={i} className="flex items-start gap-3">
                  <Check className="w-5 h-5 text-lime-400 flex-shrink-0 mt-0.5" strokeWidth={3} />
                  <span>{f}</span>
                </li>
              ))}
            </ul>
            <Button color="primary" variant="bordered" className="w-full py-6 text-base font-bold">
              Start Free Enterprise Trial &rarr;
            </Button>
          </CardBody>
        </Card>

      </div>
    </main>
  );
}