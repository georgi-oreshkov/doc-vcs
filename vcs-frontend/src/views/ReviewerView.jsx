import { useState } from 'react';
import { Check } from 'lucide-react';
import ApprovalCard from '../components/ApprovalCard'; // Import the new component

export default function ReviewerView({ setView, onSelectDoc }) {
  const [requests, setRequests] = useState([
    { id: 1, title: "Q3 Marketing Strategy", author: "Alice Smith", date: "2 hours ago", version: "v2.1", content: "Here is the detailed marketing strategy for Q3..." },
    { id: 2, title: "API Documentation Update", author: "Bob Jones", date: "5 hours ago", version: "v1.4", content: "Updated the endpoints for the authentication service..." },
    { id: 3, title: "Q4 Financial Projections", author: "Tony Reichert", date: "1 day ago", version: "v3.0", content: "Financial projections indicate a 15% growth..." },
  ]);

  const handleApprove = (id) => {
    setRequests(requests.filter(req => req.id !== id));
  };

  const handleReject = (id) => {
    setRequests(requests.filter(req => req.id !== id));
  };

  const handleViewDocument = (req) => {
    onSelectDoc(req);
    setView('viewer');
  };

  return (
    <div className="max-w-7xl mx-auto px-6 py-12 w-full z-10 flex-grow">
      <div className="mb-10">
        <h1 className="text-3xl font-bold text-white mb-2">Pending Approvals</h1>
        <p className="text-zinc-400 text-sm">Review and approve document changes requested by your team.</p>
      </div>

      {requests.length === 0 ? (
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