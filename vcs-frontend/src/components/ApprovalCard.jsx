import { Card, CardBody, Button, Chip, Avatar } from "@heroui/react";
import { Check, X, Clock, FileText } from 'lucide-react';

export default function ApprovalCard({ req, onApprove, onReject, onView }) {
  return (
    <Card className="bg-zinc-900 border border-zinc-800 w-full hover:border-zinc-700 transition-colors">
      <CardBody className="flex flex-col sm:flex-row items-start sm:items-center justify-between p-4 gap-4">
        
        {/* Clickable Left Side (Document Info) */}
        <div 
          className="flex items-center gap-4 cursor-pointer group w-full sm:w-auto"
          onClick={() => onView(req)}
        >
          <div className="w-10 h-10 rounded-lg bg-zinc-950 border border-zinc-800 flex items-center justify-center text-zinc-500 flex-shrink-0 group-hover:text-lime-400 group-hover:border-lime-500/50 transition-colors">
            <FileText size={20} />
          </div>
          <div className="flex flex-col text-left">
            <h4 className="text-white font-semibold text-base mb-1 group-hover:text-lime-400 transition-colors">{req.title}</h4>
            <div className="flex flex-wrap items-center gap-3 text-xs text-zinc-400">
              <span className="flex items-center gap-1.5">
                <Avatar name={req.author} size="sm" className="w-4 h-4 text-[8px]" /> {req.author}
              </span>
              <span className="flex items-center gap-1"><Clock size={12} /> {req.date}</span>
              <Chip size="sm" variant="bordered" className="border-zinc-700 text-zinc-400 font-mono h-5 text-[10px]">
                {req.version}
              </Chip>
            </div>
          </div>
        </div>
        
        {/* Action Buttons */}
        <div className="flex items-center gap-3 w-full sm:w-auto mt-2 sm:mt-0">
          <Button 
            color="danger" 
            variant="flat" 
            className="flex-1 sm:flex-none font-medium"
            startContent={<X size={16} />}
            onPress={() => onReject(req)}
          >
            Reject
          </Button>
          <Button 
            color="success" 
            className="flex-1 sm:flex-none text-white font-medium shadow-[0_0_15px_rgba(34,197,94,0.3)]"
            startContent={<Check size={16} />}
            onPress={() => onApprove(req)}
          >
            Approve
          </Button>
        </div>

      </CardBody>
    </Card>
  );
}