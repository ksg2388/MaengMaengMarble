import React from 'react';
import { images } from '@constants/images';
import RoomInfoCard from './RoomInfoCard';
import { Client } from '@stomp/stompjs';

type CreateRoomModalProps = {
  onClickInviteButton: () => void;
  onClickCreateRoomButton: () => void;
  clientRef: React.MutableRefObject<Client | undefined>;
};

const LobbyRoomListView = ({
  onClickInviteButton,
  onClickCreateRoomButton,
  clientRef,
}: CreateRoomModalProps) => {
  return (
    <div className='flex flex-col h-full w-full p-12 justify-between bg-white bg-opacity-50 rounded-[40px] overflow-auto'>
      <div className='grid grid-cols-2 gap-10 place-content-between pr-[20px] w-full h-full relative scrollbar'>
        <RoomInfoCard
          clientRef={clientRef}
          title='맹맹 시치 모여라~'
          currentCnt='3'
        />
        <RoomInfoCard
          clientRef={clientRef}
          title='모마말고 맹맹'
          currentCnt='1'
        />
        <RoomInfoCard clientRef={clientRef} title='맹맹맹맹' currentCnt='2' />
        <RoomInfoCard clientRef={clientRef} title='야호' currentCnt='4' />
        <RoomInfoCard clientRef={clientRef} title='맹맹맹맹' currentCnt='2' />
        <RoomInfoCard clientRef={clientRef} title='야호' currentCnt='4' />
        <RoomInfoCard clientRef={clientRef} title='맹맹맹맹' currentCnt='2' />
        <RoomInfoCard clientRef={clientRef} title='야호' currentCnt='4' />
        <RoomInfoCard clientRef={clientRef} title='맹맹맹맹' currentCnt='2' />
        <RoomInfoCard clientRef={clientRef} title='야호' currentCnt='4' />
      </div>
      <div className='flex flex-row h-[18px] items-center justify-between mt-[40px]'>
        <button
          onClick={onClickInviteButton}
          className='flex flex-row px-5 py-3 items-center justify-between bg-primary-dark100 rounded-[15px]'
        >
          <img
            className='h-[18px] mr-3'
            src={images.icon.invite}
            alt='초대코드 입력 버튼'
          />
          <p className='text-[18px] font-bold text-white'>초대 코드 입력</p>
        </button>
        <button
          onClick={onClickCreateRoomButton}
          className='flex flex-row px-5 py-3 items-center justify-between bg-primary-dark100 rounded-[15px]'
        >
          <img
            className='h-[18px] mr-3'
            src={images.icon.plus}
            alt='새로운 방 생성 버튼'
          />
          <p className='text-[18px] font-bold text-white'>새로운 방 생성</p>
        </button>
      </div>
    </div>
  );
};

export default LobbyRoomListView;
