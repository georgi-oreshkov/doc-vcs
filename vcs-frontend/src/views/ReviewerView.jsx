import { useNavigate } from 'react-router-dom';
import { Check } from 'lucide-react';
import { Spinner } from "@heroui/react";
import ApprovalCard from '../components/ApprovalCard';
import { useRequests, useActionRequest } from '../hooks/useRequests';

export default function ReviewerView() {
  const navigate = useNavigate();
  const { data: requests = [], isLoading, error } = useRequests({ status: 'PENDING' });
  const actionReq = useActionRequest();

  const handleApprove = (id) => {
    actionReq.mutate({ requestId: id, data: { approve: true } });
  };

  const handleReject = (id) => {
    actionReq.mutate({ requestId: id, data: { approve: false } });
  };

  // Navigates directly to the document for review
  const handleViewDocument = (req) => {
    // Falls back appropriately based on available data from the request payload
    if (req.org_id && req.doc_id) {
      navigate(`/organizations/${req.org_id}/documents/${req.doc_id}`);
    } else {
      navigate(`/documents/${req.doc_id}`);
    }
  };

  return (
    <div className="max-w-7xl mx-auto px-6 py-12 w-full z-10 flex-grow">
      <div className="mb-10">
        <h1 className="text-3xl font-bold text-white mb-2">Pending Approvals</h1>
        <p className="text-zinc-400 text-sm">Review and approve document changes requested by your team.</p>
      </div>

      {isLoading ? (
        <div className="flex justify-center py-20"><Spinner color="primary" size="lg" /></div>
      ) : requests.length === 0 ? (
        <div className="flex flex-col items-center justify-center py-20 text-zinc-500 bg-zinc-900/30 rounded-2xl border border-zinc-800/50">
          <Check size={48} className="mb-4 text-lime-500/50" />
          <p className="text-lg text-white">All caught up!</p>
          <p className="text-sm">You have no pending requests to review.</p>
        </div>
      ) : (
        <div className="flex flex-col gap-4">
          {requests.map(req => (
            <ApprovalCard 
              key={req.id} 
              req={req} 
              onApprove={handleApprove} 
              onReject={handleReject} 
              onView={handleViewDocument} 
            />
          ))}
        </div>
      )}
    </div>
  );
}